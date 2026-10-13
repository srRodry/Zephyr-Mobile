package xendroid.compose.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xendroid.compose.R

/** Shared Zephyr mark for onboarding, About and app updates. */
@Composable
fun XdLogo(size: Dp = 30.dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.zephyr_launcher_foreground), contentDescription = stringResource(R.string.app_name),
        modifier = modifier.size(size))
}
