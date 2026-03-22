package com.berdik.letmedowngrade

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.berdik.letmedowngrade.ui.theme.APPTheme
import com.berdik.letmedowngrade.utils.PrefManager
import com.berdik.letmedowngrade.utils.XposedChecker

class MainActivity : ComponentActivity() {

    private lateinit var isHookSwitchOn: MutableState<Boolean>

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        setTheme(R.style.Theme_LetMeDowngrade)
        super.onCreate(savedInstanceState)
        PrefManager.loadPrefs()
        isHookSwitchOn = mutableStateOf(PrefManager.isHookOn())
        PrefManager.getHookActiveAsLiveData().observe(this) { isActive ->
            isActive?.let {
                isHookSwitchOn.value = it
            }
        }

        setContent {
            APPTheme {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    AppUI()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppUI(modifier: Modifier = Modifier) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                bitmap = loadXmlDrawable(R.drawable.ic_launcher_round)!!,
                                contentDescription = stringResource(R.string.app_name)
                            )
                            Spacer(Modifier.padding(horizontal = 5.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            modifier = modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
        ) { padding ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                StatusCard(isHookSwitchOn)
            }
        }
    }

    @Composable
    fun StatusCard(isHookSwitchOn: MutableState<Boolean>) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (XposedChecker.isEnabled()) {
                        Icon(
                            painterResource(R.drawable.checklist_24),
                            contentDescription = stringResource(R.string.status_icon_content_description)
                        )
                    } else {
                        Icon(
                            painterResource(R.drawable.error_24),
                            contentDescription = stringResource(R.string.error_icon_content_description)
                        )
                    }
                    Text(
                        text = stringResource(R.string.status_title),
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!XposedChecker.isEnabled()) {
                    Text(
                        text = stringResource(R.string.module_disabled),
                        fontSize = 20.sp,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isHookSwitchOn.value) {
                                stringResource(R.string.downgrade_status_enabled)
                            } else {
                                stringResource(R.string.downgrade_status_disabled)
                            },
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = isHookSwitchOn.value,
                            onCheckedChange = { PrefManager.toggleHookState() },
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }

    /*
        loadXmlDrawable() was sourced from
        https://slack-chats.kotlinlang.org/t/506477/hello-i-am-trying-to-load-a-layer-list-drawable-with-this-co#707c4aef-021c-421b-b873-ea7ca453b61e
     */
    @Composable
    fun loadXmlDrawable(@DrawableRes resId: Int): ImageBitmap? =
        ContextCompat.getDrawable(
            LocalContext.current,
            resId
        )?.toBitmap()?.asImageBitmap()
}