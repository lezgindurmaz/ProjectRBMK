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
    val infT = rememberInfiniteTransition(label = "menu")
    val glow by infT.animateFloat(
        0.3f, 0.85f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(Modifier.fillMaxSize().background(BackgroundDark)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Başlık ─────────────────────────────────────────────────────────
            Text("RBMK-1000", color = TextGlowing.copy(alpha = glow),
                 fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            Text("NÜKLEER REAKTÖR SİMÜLATÖRÜ", color = TextSecondary,
                 fontSize = 11.sp, fontFamily = Mono, letterSpacing = 2.sp)
            Text("Smolensk AES — 2026  |  Aleksandr Bryuhanov", color = TextDim,
                 fontSize = 8.sp, fontFamily = Mono)

            Spacer(Modifier.height(10.dp))

            // İlerleme özeti
            val doneCount = completedLevels.size
            if (doneCount > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .background(ButtonOk.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .border(1.dp, ButtonOk.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("İlerleme: $doneCount / 16 Level Tamamlandı",
                         color = ButtonOk, fontSize = 8.sp, fontFamily = Mono)
                }
                Spacer(Modifier.height(6.dp))
            }

            // ── Hikaye ─────────────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth(0.55f).height(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(ButtonActive.copy(alpha = 0.2f), Color.Transparent)))
                    .border(1.dp, ButtonActive.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .clickable(onClick = onShowStory),
                contentAlignment = Alignment.Center
            ) {
                Text("HİKAYEYİ OKU — Kim bu Bryuhanov?",
                     color = ButtonActive, fontSize = 9.sp, fontFamily = Mono)
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("LEVEL SEÇİN", color = TextSecondary, fontSize = 9.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                Text("Kilitli levellar bir öncekini tamamlayınca açılır", color = TextDim, fontSize = 7.sp, fontFamily = Mono)
            }
            Spacer(Modifier.height(4.dp))

            // ── Level Izgarası ─────────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(GameLevels.ALL) { info ->
                    val unlocked  = GameLevels.isLevelUnlocked(info.number, completedLevels)
                    val completed = info.number in completedLevels
                    LevelCard(info, unlocked, completed) {
                        if (unlocked) onLevelSelect(info.number)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text("Pozitif boşluk katsayısı  •  Xenon-135  •  Grafit uç etkisi",
                 color = TextDim, fontSize = 7.sp, fontFamily = Mono)
        }
    }
}

@Composable
fun LevelCard(info: LevelInfo, unlocked: Boolean, completed: Boolean, onClick: () -> Unit) {
    val borderCol = when {
        !unlocked   -> PanelBorder
        completed   -> ButtonOk.copy(alpha = 0.7f)
        info.isHard -> AlarmEmergency.copy(alpha = 0.7f)
        info.isFinal-> Color(0xFF9C27B0).copy(alpha = 0.7f)
        else        -> Color(info.difficulty.color).copy(alpha = 0.5f)
    }
    val bgCol = when {
        !unlocked -> SurfaceDark.copy(alpha = 0.4f)
        completed -> ButtonOk.copy(alpha = 0.07f)
        else      -> SurfaceDark
    }

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(bgCol)
            .border(if (info.isHard || info.isFinal) 2.dp else 1.dp, borderCol, RoundedCornerShape(6.dp))
            .then(if (unlocked) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            Modifier.fillMaxSize().padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Numara veya kilit
            Text(
                if (!unlocked) "🔒" else "${info.number}",
                color = if (unlocked) borderCol else TextDim,
                fontSize = if (!unlocked) 16.sp else 18.sp,
                fontWeight = FontWeight.Bold, fontFamily = Mono
            )
            // Tamamlandı işareti
            if (completed) Text("✓", color = ButtonOk, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
            // Başlık
            Text(
                info.title,
                color = if (unlocked) TextSecondary else TextDim,
                fontSize = 6.sp, fontFamily = Mono,
                textAlign = TextAlign.Center, maxLines = 3, lineHeight = 8.sp
            )
            // Zorluk
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    info.difficulty.label.uppercase(),
                    color = Color(info.difficulty.color).copy(alpha = if (unlocked) 0.9f else 0.3f),
                    fontSize = 5.sp, fontFamily = Mono, fontWeight = FontWeight.Bold
                )
                if (info.isHard  && unlocked) Text("★ ZORG", color = AlarmEmergency,   fontSize = 5.sp, fontFamily = Mono)
                if (info.isFinal && unlocked) Text("★ SON",  color = Color(0xFF9C27B0), fontSize = 5.sp, fontFamily = Mono)
            }
        }
    }
}

// ─── HİKAYE ──────────────────────────────────────────────────────────────────
@Composable
fun StoryScreen(onBack: () -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(BackgroundDark)) {
        Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(scroll)) {
            Text("KİM BU ALEKSANDR BRYUHANOV?", color = TextGlowing,
                 fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            Spacer(Modifier.height(10.dp))

            StoryBlock(
                "2026 yılı. Rusya'da Smolensk nükleer enerji santralinde " +
                "RBMK-1000 reaktörü işletilmektedir. Sovyet döneminden miras " +
                "bu reaktör, modernize edilmiş olsa da temel tasarım sorunlarını taşır:\n\n" +
                "POZİTİF BOŞLUK KATSAYISI."
            )
            StoryBlock(
                "Aleksandr Bryuhanov — sizsiniz.\n\n" +
                "35 yaşında, nükleer mühendislik lisansına sahip bir kıdemli operatör. " +
                "10 yıllık deneyiminizle kontrol odasının en yetkili ismisiniz. " +
                "Bugüne kadar tek bir büyük kaza geçirmediniz. " +
                "Amirleriniz size güvenir, astlarınız size saygı duyar."
            )
            StoryBlock(
                "Göreviniz: 16 seviyeden oluşan operasyonel testleri başarıyla tamamlamak.\n" +
                "Başarılı olursanız Baş Mühendis unvanına terfi edeceksiniz.\n\n" +
                "Ama RBMK reaktörleri hata affetmez."
            )
            StoryBlock(
                "RBMK-1000 TASARIM SORUNLARI:\n\n" +
                "1. POZİTİF BOŞLUK KATSAYISI\n" +
                "   Soğutucu kaynarsa reaktivite ARTAR. Yani daha çok ısı,\n" +
                "   daha fazla kaynama, daha fazla güç, daha fazla ısı...\n" +
                "   Bu döngü kontrol edilmezse felakete yol açar.\n\n" +
                "2. GRAFİT UÇ EFEKTİ\n" +
                "   AZ-5 acil durdurma düğmesine basıldığında, çubuklar\n" +
                "   reaktöre girerken grafit uçları ilk anlarda reaktiviteyi\n" +
                "   ARTTIRIR. Ardından azaltır. Bu tasarım hatası 1986'da\n" +
                "   Çernobil'de yaşandı.\n\n" +
                "3. DÜŞÜK ORM TEHLİKESİ\n" +
                "   Operasyonel Reaktivite Marjı < 7 çubuk olursa reaktör\n" +
                "   çok az güvenlik tamponu ile çalışır. Herhangi bir arıza\n" +
                "   geri dönüşsüz sonuçlara yol açabilir."
            )

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth().height(38.dp)
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
fun StoryBlock(text: String) {
    Text(text, color = TextSecondary, fontSize = 9.sp, fontFamily = Mono,
         lineHeight = 15.sp, modifier = Modifier.padding(bottom = 12.dp))
}
