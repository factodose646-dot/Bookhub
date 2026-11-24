package com.example.cloud

import android.content.Context
import com.example.R
import com.example.model.Book
import com.example.model.BookCategory
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class CloudMigrationSummary(
    val uploadedCategories: Int,
    val uploadedBooks: Int,
    val skippedExistingBooks: Int,
)

class FirestoreCatalogRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
    )

    private fun requireUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In is required before modifying the cloud catalog.")

    fun observeCategories(): Flow<List<FirestoreCategoryDto>> =
        db.collection(COLLECTION_CATEGORIES)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreCategoryDto::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                ).sortedBy { it.sortOrder }
            }
            .catch { error ->
                val unwrapped = unwrapFirestoreCause(error) ?: throw error
                handleFirestoreError(unwrapped, OperationType.LIST, COLLECTION_CATEGORIES)
                throw unwrapped
            }

    /**
     * Publicly aligned query matching firestore.rules (`published == true`).
     */
    fun observePublishedBooks(): Flow<List<FirestoreBookDto>> =
        db.collection(COLLECTION_BOOKS)
            .whereEqualTo("published", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreBookDto::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
            }
            .catch { error ->
                val unwrapped = unwrapFirestoreCause(error) ?: throw error
                handleFirestoreError(unwrapped, OperationType.LIST, COLLECTION_BOOKS)
                throw unwrapped
            }

    /**
     * Full catalog stream (published + unpublished drafts) strictly for verified admins.
     */
    fun observeAllBooksForAdmin(): Flow<List<FirestoreBookDto>> =
        db.collection(COLLECTION_BOOKS)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreBookDto::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
            }
            .catch { error ->
                val unwrapped = unwrapFirestoreCause(error) ?: throw error
                handleFirestoreError(unwrapped, OperationType.LIST, COLLECTION_BOOKS)
                throw unwrapped
            }

    suspend fun createOrUpdateCategory(
        category: BookCategory,
        isNew: Boolean,
    ): Result<Unit> {
        val path = "$COLLECTION_CATEGORIES/${category.id}"
        return try {
            requireUid()
            val ref = db.collection(COLLECTION_CATEGORIES).document(category.id)
            if (isNew) {
                ref.set(FirestoreCategoryDto.toCreateMap(category)).await()
            } else {
                val existing = ref.get().await()
                if (existing.exists()) {
                    ref.update(FirestoreCategoryDto.toUpdateMap(category)).await()
                } else {
                    ref.set(FirestoreCategoryDto.toCreateMap(category)).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCategoryAndReassign(
        categoryId: String,
        reassignToCategory: BookCategory?,
        affectedBookIds: List<String>,
    ): Result<Unit> {
        val path = "$COLLECTION_CATEGORIES/$categoryId"
        return try {
            val uid = requireUid()
            val batch = db.batch()
            if (reassignToCategory != null) {
                val targetCatRef = db.collection(COLLECTION_CATEGORIES).document(reassignToCategory.id)
                val targetSnapshot = targetCatRef.get().await()
                if (!targetSnapshot.exists()) {
                    batch.set(targetCatRef, FirestoreCategoryDto.toCreateMap(reassignToCategory))
                }
                affectedBookIds.forEach { bookId ->
                    val bookRef = db.collection(COLLECTION_BOOKS).document(bookId)
                    batch.update(
                        bookRef,
                        mapOf(
                            "categoryId" to reassignToCategory.id,
                            "updatedByUid" to uid,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                }
            }
            batch.delete(db.collection(COLLECTION_CATEGORIES).document(categoryId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveBook(
        book: Book,
        isNew: Boolean,
    ): Result<Unit> {
        val path = "$COLLECTION_BOOKS/${book.id}"
        return try {
            val uid = requireUid()
            val batch = db.batch()
            val catRef = db.collection(COLLECTION_CATEGORIES).document(book.category.id)
            val catSnapshot = catRef.get().await()
            if (!catSnapshot.exists()) {
                batch.set(catRef, FirestoreCategoryDto.toCreateMap(book.category))
            }

            val bookRef = db.collection(COLLECTION_BOOKS).document(book.id)
            if (isNew) {
                batch.set(bookRef, FirestoreBookDto.toCreateMap(book, uid))
            } else {
                val existing = bookRef.get().await()
                if (existing.exists()) {
                    batch.update(bookRef, FirestoreBookDto.toUpdateMap(book, uid))
                } else {
                    batch.set(bookRef, FirestoreBookDto.toCreateMap(book, uid))
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setBookPublished(
        bookId: String,
        published: Boolean,
    ): Result<Unit> {
        val path = "$COLLECTION_BOOKS/$bookId"
        return try {
            val uid = requireUid()
            db.collection(COLLECTION_BOOKS).document(bookId)
                .update(
                    mapOf(
                        "published" to published,
                        "updatedByUid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteBook(bookId: String): Result<Unit> {
        val path = "$COLLECTION_BOOKS/$bookId"
        return try {
            requireUid()
            db.collection(COLLECTION_BOOKS).document(bookId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * Safely migrates local catalog categories and books into Firestore using deterministic IDs
     * so re-running never produces duplicate records and never deletes local PDFs/progress.
     */
    suspend fun migrateLocalCatalogIdempotently(
        categories: List<BookCategory>,
        books: List<Book>,
    ): Result<CloudMigrationSummary> {
        return try {
            val uid = requireUid()
            val existingCategoryIds = db.collection(COLLECTION_CATEGORIES).get().await()
                .documents.map { it.id }.toSet()
            val existingBookIds = db.collection(COLLECTION_BOOKS).get().await()
                .documents.map { it.id }.toSet()

            val missingCategories = categories.filter { it.id !in existingCategoryIds }
            val missingBooks = books.filter { it.id !in existingBookIds }

            val operations = mutableListOf<Pair<String, Map<String, Any>>>()
            missingCategories.forEach { cat ->
                operations.add("$COLLECTION_CATEGORIES/${cat.id}" to FirestoreCategoryDto.toCreateMap(cat))
            }
            missingBooks.forEach { book ->
                operations.add("$COLLECTION_BOOKS/${book.id}" to FirestoreBookDto.toCreateMap(book, uid))
            }

            // Firestore WriteBatch allows up to 500 writes per batch
            operations.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (docPath, payload) ->
                    batch.set(db.document(docPath), payload)
                }
                batch.commit().await()
            }

            Result.success(
                CloudMigrationSummary(
                    uploadedCategories = missingCategories.size,
                    uploadedBooks = missingBooks.size,
                    skippedExistingBooks = books.size - missingBooks.size,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val op = if (e is FirebaseFirestoreException) OperationType.WRITE else OperationType.CREATE
            handleFirestoreError(e, op, COLLECTION_BOOKS)
            Result.failure(e)
        }
    }

    companion object {
        const val COLLECTION_BOOKS = "books"
        const val COLLECTION_CATEGORIES = "categories"
        const val COLLECTION_ADMINS = "admins"
    }
}
