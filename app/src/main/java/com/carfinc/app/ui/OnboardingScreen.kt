package com.carfinc.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carfinc.app.data.Prefs
import com.carfinc.app.model.MatFactory
import com.carfinc.app.model.MatSize
import com.carfinc.app.util.Haptics
import com.carfinc.app.widget.WidgetUpdater
import kotlinx.coroutines.launch
import kotlin.random.Random

private data class Page(val title: String, val body: String, val color: Int, val size: MatSize, val name: String, val stickers: List<String>)

private val pages = listOf(
    Page("Create aesthetic mat widgets", "Type a name and watch it spell itself out in cut-out paper letters.",
        0xFF14402B.toInt(), MatSize.MEDIUM, "CARF", listOf("flowers_clover", "cute_star", "food_cherry")),
    Page("Change color & size", "Pick any mat color, switch between Medium and Large, and drag the sliders to feel every notch.",
        0xFFC8664A.toInt(), MatSize.LARGE, "HELLO", listOf("flowers_daisy", "cute_cat", "dynamic_bolt", "food_strawberry")),
    Page("Put it on your home screen", "Add stickers and photos, then drop the Carf.INC widget on your home screen. It updates the moment you edit.",
        0xFF2447C9.toInt(), MatSize.MEDIUM, "MINE", listOf("cute_moon", "dynamic_sparkle", "food_donut", "flowers_blossom"))
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { pages.size })
    var showHelp by remember { mutableStateOf(false) }
    val samples = remember {
        pages.mapIndexed { n, p ->
            val r = Random(40 + n)
            MatFactory.withStickers(MatFactory.create(p.name, p.color, p.size, r), p.stickers, r).copy(id = "intro$n")
        }
    }

    LaunchedEffect(pager.currentPage) { Haptics.soft(view) }

    fun finish() { Prefs.onboarded = true; onDone() }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(28)
            Spacer(Modifier.weight(1f))
            Text("Skip", color = Muted, modifier = Modifier.clip(RoundedCornerShape(10.dp))
                .clickable { Haptics.click(view); finish() }.padding(8.dp))
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MatThumb(samples[i], Modifier.fillMaxWidth(), widthPx = 760)
                }
                Spacer(Modifier.height(24.dp))
                Text(pages[i].title, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                    fontSize = 30.sp, color = Ink, textAlign = TextAlign.Center, lineHeight = 34.sp)
                Spacer(Modifier.height(10.dp))
                Text(pages[i].body, color = Muted, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(Modifier.height(20.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                Box(Modifier.padding(4.dp).size(if (pager.currentPage == i) 22.dp else 8.dp, 8.dp)
                    .clip(CircleShape).background(if (pager.currentPage == i) Green else Color(0xFFCBD2DE)))
            }
        }
        val last = pager.currentPage == pages.size - 1
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (last) "Get started" else "Next", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Green)
                    .clickable {
                        if (last) { Haptics.confirm(view); finish() }
                        else { Haptics.click(view); scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                    }.padding(vertical = 16.dp))
            if (last) {
                Text("Add the widget to my Home Screen", color = Green, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(10.dp)).clickable {
                        Haptics.click(view)
                        if (!WidgetUpdater.requestPin(ctx)) showHelp = true else finish()
                    }.padding(10.dp))
            }
        }
    }
    if (showHelp) WidgetHelpDialog { showHelp = false; finish() }
}
