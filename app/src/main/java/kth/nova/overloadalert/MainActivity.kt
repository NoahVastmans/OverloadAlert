package kth.nova.overloadalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import kth.nova.overloadalert.di.AppComponent
import kth.nova.overloadalert.ui.screens.home.HomeScreen
import kth.nova.overloadalert.ui.screens.home.HomeViewModel
import kth.nova.overloadalert.ui.theme.OverloadAlertTheme

class MainActivity : ComponentActivity() {

    private val appComponent by lazy { AppComponent(applicationContext) }

    private val homeViewModel by viewModels<HomeViewModel> { appComponent.homeViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            OverloadAlertTheme {
                HomeScreen(viewModel = homeViewModel)
            }
        }
    }
}
