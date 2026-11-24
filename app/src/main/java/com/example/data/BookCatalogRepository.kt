package com.example.data

import com.example.model.Book
import com.example.model.BookCategory
import com.example.model.BookListingType
import com.example.model.BookSortOption

/**
 * Local catalog abstraction designed so future admin-uploaded PDF books
 * can be merged or streamed without modifying UI screens.
 */
interface BookCatalogRepository {
    fun getInitialBooks(): List<Book>
}

class LocalBookCatalogRepository : BookCatalogRepository {
    override fun getInitialBooks(): List<Book> = BookSampleData.books
}

object BookCatalogQueries {

    fun filterForListing(
        books: List<Book>,
        listingType: BookListingType,
        category: BookCategory?,
        query: String,
        sortOption: BookSortOption,
    ): List<Book> {
        val base = when (listingType) {
            BookListingType.CATEGORY -> books.filter { category == null || it.category.id == category.id }
            BookListingType.FEATURED -> books.filter { it.isFeatured }
            BookListingType.POPULAR -> books.filter { it.isPopular }
            BookListingType.RECENT -> books.filter { it.isRecent }
            BookListingType.CONTINUE_READING -> books.filter { it.readingProgress > 0f }
            BookListingType.ALL -> books
        }

        val searched = filterByQuery(base, query)
        return sortBooks(searched, sortOption)
    }

    fun filterByQuery(books: List<Book>, query: String): List<Book> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return books
        return books.filter { book ->
            book.title.contains(trimmed, ignoreCase = true) ||
                book.category.displayName.contains(trimmed, ignoreCase = true) ||
                book.author.contains(trimmed, ignoreCase = true)
        }
    }

    fun sortBooks(books: List<Book>, sortOption: BookSortOption): List<Book> = when (sortOption) {
        BookSortOption.NEWEST -> books.sortedWith(
            compareByDescending<Book> { it.publishedYear }
                .thenByDescending { it.addedOrder },
        )
        BookSortOption.OLDEST -> books.sortedWith(
            compareBy<Book> { it.publishedYear }
                .thenBy { it.addedOrder },
        )
        BookSortOption.A_TO_Z -> books.sortedBy { it.title.lowercase() }
    }

    fun getRelatedBooks(books: List<Book>, currentBook: Book): List<Book> =
        books
            .filter { it.category.id == currentBook.category.id && it.id != currentBook.id }
            .sortedByDescending { it.rating }
}
