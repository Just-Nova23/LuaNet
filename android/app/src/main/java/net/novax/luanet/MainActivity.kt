package net.novax.luanet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import net.novax.luanet.ui.LuaNetApp
import net.novax.luanet.ui.LuaNetTheme
import net.novax.luanet.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LuaNetTheme { LuaNetApp(viewModel) } }
    }
}
