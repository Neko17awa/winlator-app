package com.winlator.ui.component

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.winlator.R

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "?"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Icon(
                Icons.Outlined.Inventory2,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Winlator Next",
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${stringResource(R.string.version)} $versionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Material Design 3 Expressive UI Branch",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "基于 Winlator 的 MD3E 风格重构版本，采用 Jetpack Compose + Material 3 Expressive 全新设计语言，支持动态配色、圆角卡片、LargeTopAppBar 等现代 Android UI 特性。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                val linkColor = MaterialTheme.colorScheme.primary
                val repoString = buildAnnotatedString {
                    pushStringAnnotation(tag = "URL", annotation = "https://github.com/Neko17awa/winlator-Next")
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append("github.com/Neko17awa/winlator-Next")
                    }
                    pop()
                }
                ClickableText(
                    text = repoString,
                    style = MaterialTheme.typography.bodyMedium,
                    onClick = { offset ->
                        repoString.getStringAnnotations("URL", offset, offset)
                            .firstOrNull()?.let { uriHandler.openUri(it.item) }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                val webString = buildAnnotatedString {
                    pushStringAnnotation(tag = "URL", annotation = "https://www.winlator.org")
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append("winlator.org")
                    }
                    pop()
                }
                ClickableText(
                    text = webString,
                    style = MaterialTheme.typography.bodyMedium,
                    onClick = { offset ->
                        webString.getStringAnnotations("URL", offset, offset)
                            .firstOrNull()?.let { uriHandler.openUri(it.item) }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.credits_and_third_party_apps),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))

                val credits = listOf(
                    "GLIBC Patches" to "https://github.com/termux-pacman/glibc-packages",
                    "Wine" to "https://www.winehq.org",
                    "Box86/Box64 by ptitseb" to "https://github.com/ptitSeb",
                    "Mesa (Turnip/Zink/VirGL)" to "https://www.mesa3d.org",
                    "DXVK" to "https://github.com/doitsujin/dxvk",
                    "VKD3D" to "https://gitlab.winehq.org/wine/vkd3d",
                    "CNC DDraw" to "https://github.com/FunkyFr3sh/cnc-ddraw",
                )

                credits.forEach { (name, url) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        SuggestionChip(
                            onClick = { uriHandler.openUri(url) },
                            label = {
                                Text(
                                    "Link",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}
