package com.faisal.iltizam

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.faisal.iltizam.data.Settings
import com.faisal.iltizam.ui.AppViewModel
import com.faisal.iltizam.ui.DetailScreen
import com.faisal.iltizam.ui.EditScreen
import com.faisal.iltizam.ui.HomeScreen
import com.faisal.iltizam.ui.IltizamTheme
import com.faisal.iltizam.ui.SettingsScreen

sealed class Screen {
    data object Home : Screen()
    data class Detail(val id: String) : Screen()
    data class Edit(val id: String?) : Screen()
    data object Settings : Screen()
}

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            IltizamTheme {
                val vm: AppViewModel = viewModel()
                val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
                val positions by vm.positions.collectAsState()
                fun push(s: Screen) { stack.add(s) }
                fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

                BackHandler(enabled = stack.size > 1) { pop() }

                when (val s = stack.last()) {
                    Screen.Home -> HomeScreen(
                        vm = vm,
                        hasApiKey = Settings(this).apiKey.isNotBlank(),
                        onOpen = { push(Screen.Detail(it)) },
                        onAdd = { push(Screen.Edit(null)) },
                        onSettings = { push(Screen.Settings) },
                    )
                    is Screen.Detail -> {
                        val p = positions.firstOrNull { it.id == s.id }
                        if (p == null) LaunchedEffect(s) { pop() }
                        else DetailScreen(p, vm, onEdit = { push(Screen.Edit(p.id)) }, onBack = { pop() })
                    }
                    is Screen.Edit -> EditScreen(
                        existing = s.id?.let { id -> positions.firstOrNull { it.id == id } },
                        onSave = { vm.save(it); pop() },
                        onBack = { pop() },
                    )
                    Screen.Settings -> SettingsScreen(onBack = { pop(); vm.refresh() })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // العامل في الخلفية ممكن حدّث الملف
        com.faisal.iltizam.data.Repository.get(this).reload()
    }
}
