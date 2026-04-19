package com.rbmk.alexandr.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rbmk.alexandr.model.GameLevels
import com.rbmk.alexandr.model.LevelInfo
import com.rbmk.alexandr.ui.theme.*

// ─── ANA MENÜ ────────────────────────────────────────────────────────────────
@Composable
fun MainMenuScreen(
    completedLevels: Set<Int>,
    onLevelSelect: (Int) -> Unit,
    onShowStory: () -> Unit
) {
    val infiniteT = rememberInfiniteTransition(label = "menu")
    val glowAlpha by infiniteT.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(10.dp))

            // ── Başlık ────────────────────────────────────────────────────────
            Text("РБМК-1000", color = TextGlowing.copy(alpha = glowAlpha),
                 fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            Text("NÜKLEER REAKTÖR SİMÜLATÖRü", color = TextSecondary,
                 fontSize = 11.sp, fontFamily = Mono, letterSpacing = 2.sp)
            Text("Smolensk AES — 2026", color = TextDim, fontSize = 9.sp, fontFamily = Mono)
            Text("Aleksandr Bryuhanov olarak oynuyorsunuz",
                 color = TextRussia, fontSize = 8.sp, fontFamily = Mono)

            Spacer(Modifier.height(12.dp))

            // ── Hikaye Butonu ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f).height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(
                        listOf(ButtonActive.copy(alpha = 0.2f), Color.Transparent)
                    ))
                    .border(1.dp, ButtonActive.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .clickable(onClick = onShowStory),
                contentAlignment = Alignment.Center
            ) {
                Text("HİKAYEYİ OKU — КТО ТАКОЙ БРЮХАНОВ?",
                     color = ButtonActive, fontSize = 9.sp, fontFamily = Mono)
            }

            Spacer(Modifier.height(14.dp))
            Text("LEVEL SEÇİN — ВЫБОР ЗАДАНИЯ", color = TextSecondary,
                 fontSize = 9.sp, fontFamily = Mono, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))

            // ── Level Izgarası ────────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(GameLevels.ALL) { levelInfo ->
                    val unlocked  = GameLevels.isLevelUnlocked(levelInfo.number, completedLevels)
                    val completed = levelInfo.number in completedLevels
                    LevelCard(
                        info      = levelInfo,
                        unlocked  = unlocked,
                        completed = completed,
                        onClick   = { if (unlocked) onLevelSelect(levelInfo.number) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Pozitif boşluk katsayisi  •  Xenon-135 dinamikleri  •  Grafit uc etkisi",
                 color = TextDim, fontSize = 7.sp, fontFamily = Mono)
        }
    }
}

@Composable
fun LevelCard(
    info: LevelInfo,
    unlocked: Boolean,
    completed: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        !unlocked  -> PanelBorder
        completed  -> ButtonOk.copy(alpha = 0.7f)
        info.isHard -> AlarmEmergency.copy(alpha = 0.7f)
        info.isFinal -> Color(0xFF9C27B0).copy(alpha = 0.7f)
        else       -> Color(info.difficulty.color).copy(alpha = 0.5f)
    }
    val bgColor = when {
        !unlocked -> SurfaceDark.copy(alpha = 0.4f)
        completed -> ButtonOk.copy(alpha = 0.07f)
        else      -> SurfaceDark
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(
                width = if (info.isHard || info.isFinal) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .then(if (unlocked) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (!unlocked) "🔒" else "${info.number}",
                color = if (unlocked) borderColor else TextDim,
                fontSize = if (!unlocked) 16.sp else 20.sp,
                fontWeight = FontWeight.Bold, fontFamily = Mono
            )
            if (completed) Text("✓", color = ButtonOk, fontSize = 12.sp, fontFamily = Mono)
            Text(
                info.title,
                color = if (unlocked) TextSecondary else TextDim,
                fontSize = 6.sp, fontFamily = Mono,
                textAlign = TextAlign.Center, maxLines = 3, lineHeight = 9.sp
            )
            Text(
                info.difficulty.label.uppercase(),
                color = Color(info.difficulty.color).copy(alpha = if (unlocked) 0.9f else 0.3f),
                fontSize = 6.sp, fontFamily = Mono, fontWeight = FontWeight.Bold
            )
            if (info.isHard  && unlocked) Text("★ ZORG", color = AlarmEmergency,   fontSize = 5.sp, fontFamily = Mono)
            if (info.isFinal && unlocked) Text("★ SON",  color = Color(0xFF9C27B0), fontSize = 5.sp, fontFamily = Mono)
        }
    }
}

// ─── HİKAYE EKRANI ───────────────────────────────────────────────────────────
@Composable
fun StoryScreen(onBack: () -> Unit) {
    val scroll = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(scroll)
        ) {
            Text("KİMDİR ALEKSANDR BRYUHANOV?", color = TextGlowing,
                 fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            Text("АЛЕКСАНДР БРЮХАНОВ — ОПЕРАЦИОННЫЙ ДИРЕКТОР",
                 color = TextRussia, fontSize = 9.sp, fontFamily = Mono)
            Spacer(Modifier.height(12.dp))

            StoryParagraph(
                "2026 yılı. Rusya'da Smolensk nükleer enerji santralinde, " +
                "RBMK-1000 reaktörlerinden biri işletilmektedir. " +
                "Sovyet dönemi mimarisi üzerine inşa edilmiş bu reaktör, " +
                "modernize edilmiş olsa da temel tasarım sorunlarını barındırmaktadır:\n\n" +
                "POZİTİF BOŞLUK KATSAYISI."
            )
            StoryParagraph(
                "Aleksandr Bryuhanov — sizsiniz.\n\n" +
                "35 yaşında, nükleer mühendislik lisansına sahip bir kıdemli " +
                "operatörsünüz. 10 yıllık deneyiminizle kontrol odasının en yetkili " +
                "isimlerinden birisiniz. Bugüne kadar tek bir büyük kaza geçirmediniz. " +
                "Patronlarınız sizi sever, astlarınız size güvenir."
            )
            StoryParagraph(
                "Göreviniz: 16 seviyeden oluşan operasyonel testleri başarıyla tamamlamak. " +
                "Başarılı olursanız Baş Mühendis unvanına terfi edeceksiniz.\n\n" +
                "Ama RBMK reaktörleri affetmez. " +
                "Bir hata — tek bir hata — sonuçları felaket olabilir."
            )
            StoryParagraph(
                "RBMK-1000 HAKKINDA:\n" +
                "Grafit moderatörlü, su soğutmalı kanallı reaktör\n" +
                "1000 MWe (3200 MW termal) nominal güç\n" +
                "211 kontrol ve emici çubuk\n" +
                "1660 adet yakıt kanalı\n\n" +
                "TASARIM SORUNLARI:\n" +
                "Pozitif boşluk katsayısı: Soğutucu kaynarsa reaktivite ARTAR\n" +
                "Grafit uç etkisi: AZ-5 basıldığında ilk anlarda reaktivite ARTAR\n" +
                "Düşük güçte dengesizlik: %20 altında kontrol zorlaşır\n" +
                "Minimum ORM: 15 çubuk — bunun altı tehlike bölgesi"
            )

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ButtonActive.copy(alpha = 0.15f))
                    .border(1.dp, ButtonActive, RoundedCornerShape(6.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("ANA MENÜYE DÖN", color = ButtonActive, fontSize = 10.sp,
                     fontWeight = FontWeight.Bold, fontFamily = Mono)
            }
        }
    }
}

@Composable
fun StoryParagraph(text: String) {
    Text(text, color = TextSecondary, fontSize = 9.sp, fontFamily = Mono,
         lineHeight = 15.sp, modifier = Modifier.padding(bottom = 12.dp))
}
