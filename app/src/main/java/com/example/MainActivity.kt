package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.LocalPasscodeAdminAuthGateway
import com.example.auth.SharedPreferencesAdminAuthPersistence
import com.example.cloud.FirestoreCatalogRepository
import com.example.cloud.GoogleDriveGateway
import com.example.data.LocalBookCatalogRepository
import com.example.data.LocalBookStorageGateway
import com.example.data.SharedPreferencesCatalogPersistence
import com.example.data.SharedPreferencesFavoritesPersistence
import com.example.data.SharedPreferencesReaderStatePersistence
import com.example.ui.BookHubApp
import com.example.ui.BookHubViewModel
import com.example.ui.theme.BookHubTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        val appContext = applicationContext
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val firestoreDb = FirebaseFirestore.getInstance(databaseId)
        val firebaseAuth = Firebase.auth
        val driveGateway = GoogleDriveGateway(appContext)
        val firestoreCatalogRepo = FirestoreCatalogRepository(
            db = firestoreDb,
            auth = firebaseAuth,
        )

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BookHubViewModel(
                    repository = LocalBookCatalogRepository(),
                    favoritesPersistence = SharedPreferencesFavoritesPersistence(appContext),
                    readerPersistence = SharedPreferencesReaderStatePersistence(appContext),
                    catalogPersistence = SharedPreferencesCatalogPersistence(appContext),
                    storageGateway = LocalBookStorageGateway(appContext),
                    adminAuthGateway = LocalPasscodeAdminAuthGateway(
                        persistence = SharedPreferencesAdminAuthPersistence(appContext),
                    ),
                    firestoreCatalogRepository = firestoreCatalogRepo,
                    googleDriveGateway = driveGateway,
                    firebaseAuth = firebaseAuth,
                    firestoreDb = firestoreDb,
                    applicationContext = appContext,
                ) as T
            }
        }
        setContent {
            BookHubTheme {
                val bookHubViewModel: BookHubViewModel = viewModel(factory = factory)
                LaunchedEffect(Unit) {
                    bookHubViewModel.trySilentGoogleSignIn()
                }
                BookHubApp(viewModel = bookHubViewModel)
            }
        }
    }
}
