package com.rbmk.alexandr.model

// ─── REAKTÖR DURUMLARI ───────────────────────────────────────────────────────
enum class ReactorStatus(val displayName: String, val colorCode: Int) {
    COLD_SHUTDOWN   ("Soğuk Kapalı",          0xFF607D8B.toInt()),
    HOT_SHUTDOWN    ("Sıcak Kapalı",          0xFF78909C.toInt()),
    STARTUP         ("Başlatma",              0xFF42A5F5.toInt()),
    LOW_POWER       ("Düşük Güç",             0xFF66BB6A.toInt()),
    POWER_ASCENT    ("Güç Artışı",            0xFF26A69A.toInt()),
    FULL_POWER      ("Tam Güç",               0xFF00E676.toInt()),
    POWER_REDUCTION ("Güç Azaltma",           0xFFFF9800.toInt()),
    TRANSIENT       ("Geçici Durum",          0xFFFF6F00.toInt()),
    SCRAM           ("SCRAM - ACİL DURDURMA", 0xFFE53935.toInt()),
    MELTDOWN        ("ÇEKİRDEK HASARI",       0xFFB71C1C.toInt()),
    EXPLOSION       ("REAKTÖR PATLAMASI",     0xFFFF1744.toInt())
}

// ─── ALARM MODELİ ────────────────────────────────────────────────────────────
data class AlarmModel(
    val type: Int,
    val severity: Int,
    val codeRu: String,
    val messageTr: String,
    val acknowledged: Boolean
) {
    val severityName get() = when (severity) {
        0    -> "BİLGİ"
        1    -> "UYARI"
        2    -> "ALERT"
        3    -> "ACİL"
        4    -> "KRİTİK"
        else -> "?"
    }
    val isEmergency get() = severity >= 3
    val isWarning   get() = severity >= 1
}

// ─── LOG KAYDI MODELİ ────────────────────────────────────────────────────────
data class LogEntryModel(
    val timestamp: Double,
    val message: String,
    val category: String,
    val severity: Int
) {
    val colorInt get() = when {
        category == "FATAL"    -> 0xFFFF1744.toInt()
        severity  >= 3         -> 0xFFE53935.toInt()
        severity  >= 2         -> 0xFFFF9800.toInt()
        severity  >= 1         -> 0xFFFFEB3B.toInt()
        category == "OPERATÖR" -> 0xFF42A5F5.toInt()
        category == "SİSTEM"   -> 0xFF78909C.toInt()
        else                   -> 0xFFB0BEC5.toInt()
    }
}

// ─── TAM REAKTÖR DURUMU (Kotlin tarafı) ──────────────────────────────────────
data class ReactorStateModel(
    val powerMW: Double            = 0.0,
    val powerPercent: Double       = 0.0,
    val powerRateMWs: Double       = 0.0,
    val neutronFlux: Double        = 0.0,
    val reactivityTotal: Double    = 0.0,
    val reactivityRods: Double     = 0.0,
    val reactivityXenon: Double    = 0.0,
    val reactivityVoid: Double     = 0.0,
    val reactivityDoppler: Double  = 0.0,
    val xenonLevel: Double         = 0.0,
    val iodineLevel: Double        = 0.0,
    val orm: Int                   = 211,
    val coolantTempIn: Double      = 265.0,
    val coolantTempOut: Double     = 284.0,
    val coolantPressure: Double    = 6.9,
    val voidFraction: Double       = 0.0,
    val fuelTemp: Double           = 20.0,
    val steamPressure: Double      = 0.0,
    val steamFlow: Double          = 0.0,
    val feedwaterFlow: Double      = 0.0,
    val drumLevel1: Double         = 50.0,
    val drumLevel2: Double         = 50.0,
    val coolantFlowTotal: Double   = 0.0,
    val coolantLeak: Boolean       = false,
    val leakRate: Double           = 0.0,
    val mcpFlow: DoubleArray       = DoubleArray(8),
    val mcpSpeed: DoubleArray      = DoubleArray(8),
    val mcpActive: BooleanArray    = BooleanArray(8),
    val mcpFailed: BooleanArray    = BooleanArray(8),
    val turbineSpeed: DoubleArray  = DoubleArray(2),
    val turbineLoad: DoubleArray   = DoubleArray(2),
    val turbineValve: DoubleArray  = DoubleArray(2),
    val turbineOnline: BooleanArray = BooleanArray(2),
    val turbineTrip: BooleanArray  = BooleanArray(2),
    val rodPositions: DoubleArray  = DoubleArray(211),
    val scramActive: Boolean        = false,
    val eccsActive: Boolean         = false,
    val az5Pressed: Boolean         = false,
    val reactorDestroyed: Boolean   = false,
    val missionFailed: Boolean      = false,
    val missionComplete: Boolean    = false,
    val stationBlackout: Boolean    = false,
    val dieselActive: Boolean       = false,
    val fuelChannelRupture: Boolean = false,
    val fuelMelt: Boolean           = false,
    val arActive: Boolean           = true,
    val skalaActive: Boolean        = true,
    val az5Blocked: Boolean         = false,
    val status: ReactorStatus       = ReactorStatus.COLD_SHUTDOWN,
    val simulationTime: Double      = 0.0,
    val alarms: List<AlarmModel>    = emptyList(),
    val failureReason: String       = "",
    val missionScore: Double        = 0.0
) {
    val hasActiveAlarms   get() = alarms.isNotEmpty()
    val hasEmergencyAlarm get() = alarms.any { it.isEmergency }

    val powerColor get() = when {
        reactorDestroyed     -> 0xFFFF1744.toInt()
        powerPercent > 107.0 -> 0xFFE53935.toInt()
        powerPercent > 100.0 -> 0xFFFF6F00.toInt()
        powerPercent > 10.0  -> 0xFF00E676.toInt()
        powerPercent > 0.0   -> 0xFF42A5F5.toInt()
        else                 -> 0xFF607D8B.toInt()
    }

    val ormColor get() = when {
        orm < 7  -> 0xFFE53935.toInt()
        orm < 15 -> 0xFFFF9800.toInt()
        orm < 30 -> 0xFFFFEB3B.toInt()
        else     -> 0xFF00E676.toInt()
    }
}

// ─── LEVEL BİLGİSİ ───────────────────────────────────────────────────────────
data class LevelInfo(
    val number: Int,
    val title: String,
    val subtitle: String,
    val difficulty: Difficulty,
    val description: String,
    val objectives: List<String>,
    val hints: List<String>,
    val briefing: String,
    val isHard: Boolean  = false,
    val isFinal: Boolean = false
) {
    enum class Difficulty(val label: String, val color: Int) {
        TUTORIAL ("Oryantasyon", 0xFF42A5F5.toInt()),
        EASY     ("Kolay",      0xFF66BB6A.toInt()),
        MEDIUM   ("Orta",       0xFFFFEB3B.toInt()),
        HARD     ("Zor",        0xFFFF9800.toInt()),
        EXTREME  ("Çok Zor",    0xFFE53935.toInt()),
        SCRIPTED ("Senaryo",    0xFF9C27B0.toInt())
    }
}

// ─── TÜM LEVEL TANIMLARI ─────────────────────────────────────────────────────
object GameLevels {

    val ALL: List<LevelInfo> = listOf(

        LevelInfo(
            number = 1, title = "Kontrol Paneli Oryantasyonu",
            subtitle = "Reaktörünüzü Tanıyın",
            difficulty = LevelInfo.Difficulty.TUTORIAL,
            description = "İlk gününüz. Kontrol panelindeki her sistemi tanıyın.",
            objectives = listOf(
                "Her pompa grubunu (GTs-1 ile GTs-8) panelden tanımlayın",
                "AZ-5 düğmesinin yerini öğrenin",
                "Güç göstergelerini okuyun (% ve MW)",
                "ORM göstergesini kontrol edin",
                "Buhar basıncı ve tambur seviyelerini okuyun",
                "Log panelinde sistem mesajlarını inceleyin"
            ),
            hints = listOf(
                "AZ-5 büyük kırmızı düğmedir — reaktörü acil kapatır",
                "ORM minimum 15 çubukta tutulmalıdır",
                "GTs = Главный циркуляционный насос (Ana Dolaşım Pompası)",
                "BS = Барабан-сепаратор (Tambur-Separatör)",
                "Sağ panelde tüm parametreleri izleyebilirsiniz"
            ),
            briefing = """
ORYANTASYON KILAVUZU — Aleksandr Bryuhanov

Hoş geldiniz. Bugün Smolensk RBMK-1000 reaktörünün kontrol odasındasınız.

KONTROL PANELİ DÜZENİ:
━━━━━━━━━━━━━━━━━━━━━━
• Sol üst   : Reaktör çekirdek görselleştirmesi
• Sağ üst   : Anlık göstergeler (güç, sıcaklık, basınç, ORM)
• Alt       : Kontrol paneli (çubuklar, pompalar, valfler, güvenlik)
• Sağ sütun : Log paneli (gerçek zamanlı sistem mesajları)

RBMK-1000 TEMEL ÖZELLİKLERİ:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Nominal termal güç    : 3200 MW
• Nominal elektrik gücü : 1000 MWe
• Kontrol çubuğu sayısı : 211
• Ana dolaşım pompası   : 8 adet (2 devre x 4)
• Buhar separatörü      : 2 adet (BS-1, BS-2)

ÖNEMLİ UYARI:
━━━━━━━━━━━━
RBMK reaktörünün POZİTİF BOŞLUK KATSAYISI vardır.
Soğutucu kaynarsa reaktivite ARTTIĞI anlamına gelir.
Düşük güçlerde bu etki son derece tehlikelidir.
            """.trimIndent()
        ),

        LevelInfo(
            number = 2, title = "Soğuk Başlatma Prosedürü",
            subtitle = "Reaktörü Sıfırdan Çalıştırın",
            difficulty = LevelInfo.Difficulty.EASY,
            description = "Reaktör tamamen soğuk ve kapalı. Doğru başlatma prosedürünü uygulayın.",
            objectives = listOf(
                "En az 4 GTs pompasını başlatın",
                "Besleme suyu sistemini devreye alın",
                "Kontrol çubuklarını yavaşça çekin (ORM geq 30)",
                "Reaktörü kritikliğe getirin (%1 güç)",
                "Gücü yavaşça %5'e taşıyın",
                "Parametreleri 5 dakika boyunca stabil tutun"
            ),
            hints = listOf(
                "Çubukları çok hızlı çekmeyin! Max %2 güç/dakika",
                "Xenon henüz yok — başlatma görece kolay",
                "Pompaları çalıştırmadan çubukları çekmeyin",
                "Soğutucu basıncı > 6 MPa olmalı",
                "ORM 30'un altına düşmesin"
            ),
            briefing = """
SOĞUK BAŞLATMA PROSEDÜRü — Seviye 2

Reaktör tamamen soğuk ve kapalı durumda. Xenon birikimi yok.

BAŞLATMA SIRASI:
━━━━━━━━━━━━━━━━
1. GTs-1, GTs-2, GTs-5, GTs-6 pompalarini baslatin
2. Besleme suyu setpointini %20'ye ayarlayin
3. Sistem basincinin > 6 MPa olmasini bekleyin
4. Cubuklari yavascca cekin
5. %1 guce ulasinca bekleyin
6. Yavascca %5'e tasıyın

UYARILAR:
━━━━━━━━━
Cubuk cekme hizi: max 15 cubuk/dakika
ORM her zaman geq 30 tutulmali
Tambur seviyeleri %20-80 arasinda tutulmali
            """.trimIndent()
        ),

        LevelInfo(
            number = 3, title = "Güç Artırma",
            subtitle = "%5'ten %50'ye Kontrollü Yükseliş",
            difficulty = LevelInfo.Difficulty.EASY,
            description = "Reaktör %5 güçte stabil. Şebeke talebi arttı — %50'ye çıkarmanız gerekiyor.",
            objectives = listOf(
                "Güç artış hızını < %2/dakikada tutun",
                "Kalan 2 pompa çiftini devreye alın",
                "Türbin-1'i devreye alın",
                "%50 güce ulaşın ve 10 dakika stabil tutun",
                "ORM geq 25 tutun"
            ),
            hints = listOf(
                "Güç arttıkça xenon da birikmeye başlar — bu normal",
                "Tambur seviyeleri güç artışında düşebilir; besleme suyunu artırın",
                "Türbin devreye alınmadan buhar basıncı yükselebilir",
                "AR sistemi küçük sapmaları otomatik düzeltir"
            ),
            briefing = """
GÜÇ ARTIRMA — Seviye 3

Sebebeke operatoru %50 guc talep ediyor.

KONTROLLÜ GÜÇ ARTISI:
━━━━━━━━━━━━━━━━━━━━━
Hiz: max %2 güç / dakika (yaklasik 64 MW/dk)
Kontrol cubuklarini kademeli geri cekin
Her adimda sistemi 1-2 dakika izleyin

XENON BİRİKİMİ:
━━━━━━━━━━━━━━━
Güc artikca I-135 Xe-135 donusumu baslar.
Normal surectir. Birkaç saat içinde denge
seviyesine ulasir.
            """.trimIndent()
        ),

        LevelInfo(
            number = 4, title = "Normal Operasyon Vardiyası",
            subtitle = "%100'e Ulaşın ve Vardiyayı Tamamlayın",
            difficulty = LevelInfo.Difficulty.EASY,
            description = "8 saatlik vardiya. %100 güce çıkıp tüm shift boyunca reaktörü stabil tutun.",
            objectives = listOf(
                "%100 nominal güce ulaşın (3200 MW termal)",
                "Güç sapmasını ±%5 içinde tutun",
                "ORM geq 15 (minimum güvenlik marjı)",
                "Tambur seviyeleri %30-70 arasında kalmalı",
                "30 dakika boyunca tüm parametreler limitlerin içinde olmalı",
                "Yakıt sıcaklığı < 1200°C"
            ),
            hints = listOf(
                "AR sistemi küçük sapmaları otomatik yönetir",
                "Xenon denge seviyesinde reaktivite hafif düşük; birkaç çubuk çekin",
                "Tüm 8 pompa çalışmalı",
                "Tambur seviyesi için besleme suyu setpointini ince ayarlayın"
            ),
            briefing = """
NORMAL OPERASYON — Seviye 4

Görev: 30 dakika boyunca reaktörü %100 güçte, stabil tutmak.

OPERASYON KURALLARI:
━━━━━━━━━━━━━━━━━━━
Güç              : 3200 MW ± 160 MW (%5)
ORM              : minimum 15 cubuk esdeğeri
Tambur seviyesi  : %30-70
Soğutucu çıkış T : < 295°C
Buhar basıncı    : 6.2-6.9 MPa

VARDIYA KONTROL RUTİNİ:
━━━━━━━━━━━━━━━━━━━━━━
Her 5 dakikada bir kontrol edin:
Güç seviyesi ve trendi
ORM degeri
Pompa akışları
Tambur seviyeleri
Alarm durumu
            """.trimIndent()
        ),

        LevelInfo(
            number = 5, title = "Ana Dolaşım Pompası Arızası",
            subtitle = "GTs-3 Ani Arızası — Acil Tepki!",
            difficulty = LevelInfo.Difficulty.HARD,
            isHard = true,
            description = "ZORG! %100 güçte çalışırken GTs-3 ani olarak arıza yapacak.",
            objectives = listOf(
                "GTs-3 arızası: DERHAL güç azaltın",
                "Gücü %75'in altına düşürün",
                "Soğutucu çıkış sıcaklığını < 320°C altında tutun",
                "Kalan 7 pompa ile akışı dengelendirin",
                "5 dakika boyunca sistemi stabil tutun",
                "SCRAM YAPMADAN görevi tamamlayın"
            ),
            hints = listOf(
                "Pompa arızası soğutmayı azaltır — güç AZALTILMALIDIR",
                "Void fraksiyonu yükselirse çubukları sokun",
                "Süphede kalirsaniz AZ-5'e basin",
                "Kalan pompalar için akış setpointini artırın",
                "Soğutucu sıcaklığı 320°C'yi geçerse yakıt hasarı başlar"
            ),
            briefing = """
ACİL POMPA ARIZASI — Seviye 5 (ZORG)

Saat 02:00. GTs-3 elektrik arizasi nedeniyle otomatik trip edildi.
Soğutucu akışı yaklaşık %12 düştü.

HEMEN YAPILMASI GEREKENLER:
━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. Reaktör gücünü DERHAL azaltın (çubukları sokun)
2. Güç %75 veya altında olmali
3. Void fraksiyonunu izleyin
4. Soğutucu çıkış sıcaklığını takip edin

RBMK BOŞLUK KATSAYISI:
━━━━━━━━━━━━━━━━━━━━━━
Pompa kaybi → azalan akış → daha fazla kaynama → void artar
RBMK'da artan void = ARTAN REAKTİVİTE!

BAŞARISIZLIK KOŞULLARI:
━━━━━━━━━━━━━━━━━━━━━━
Soğutucu sıcaklığı > 335°C → Yakıt hasarı
Reaktör patlaması
            """.trimIndent()
        ),

        LevelInfo(
            number = 6, title = "Türbin-2 Coast-Down Testi",
            subtitle = "%75 Güçte Türbin Ataletini Ölçün",
            difficulty = LevelInfo.Difficulty.MEDIUM,
            description = "Türbin-2'nin dönme ataletini test edin.",
            objectives = listOf(
                "Gücü %75'e düşürün (2400 MW termal)",
                "Türbin-2'yi şebekeden ayırın (trip etmeyin)",
                "Türbin-2 coast-down süresini 45+ saniye tutun",
                "Bu sürede reaktör gücünü ±%5 stabil tutun",
                "Test sonrası gücü tekrar %100'e çıkarın"
            ),
            hints = listOf(
                "Güç düşüşü xenon birikimini değiştirir — dikkatli olun",
                "Türbin coast-down: türbin şebekeden ayrılır ama buhar kesilmez",
                "Bu test pompalar için yedek güç kaynağını test eder",
                "ORM geq 20 tutun bu sürede"
            ),
            briefing = """
TÜRBİN COAST-DOWN TESTİ — Seviye 6

Bu test, türbinin dönme ataletinin reaktör güvenliği için
yeterli süre elektrik üretip üretemeyeceğini test eder.

TEST PROSEDÜRü:
━━━━━━━━━━━━━━
1. Güç %75'e düşür
2. Türbin-2'yi şebekeden ayır
3. Türbin ataleti ile dönmeye devam eder
4. Bu sürede pompalar elektrik alır
5. Türbin 45 saniye sonra hız kaybeder → test tamamlandı

NOT: Bu test 1986'da Çernobil'de de yapılmak istenmiş...
            """.trimIndent()
        ),

        LevelInfo(
            number = 7, title = "Xenon Tuzağından Çıkış",
            subtitle = "Yüksek Xenon ile Reaktörü Yeniden Başlatın",
            difficulty = LevelInfo.Difficulty.MEDIUM,
            description = "Reaktör 8 saat önce kapatıldı. Xenon zirveye ulaştı.",
            objectives = listOf(
                "Xenon seviyesini izleyin (> 1.8 ise başlatma imkansız)",
                "Xenon düşerken başlatma penceresini yakalayın",
                "Reaktörü %30'a kadar getirin",
                "ORM < 10'un altına DÜŞMEYİN",
                "Xenon burn-out başlayana kadar tutun"
            ),
            hints = listOf(
                "Xenon max yaklasik 8-12 saat sonra azalmaya başlar",
                "Çok fazla çubuk çekerseniz ORM tehlikeli düşer",
                "Xenon > 1.8 ile başlatmak neredeyse imkansız",
                "Başlatma penceresi: Xenon 1.5-1.8 arası düşerken"
            ),
            briefing = """
XENON TUZAĞI — Seviye 7

Xenon-135 nükleer reaktörlerin en büyük düşmanıdır.

XENON KİNETİKLERİ:
━━━━━━━━━━━━━━━━━━
Reaktör kapatıldı → I-135 bozunmaya devam eder → Xe-135 üretir
Xe-135 nötronları absorbe eder → negatif reaktivite
Zirve: kapanmadan 8-12 saat sonra
Sonra: Xe-135 de bozunur → reaktivite döner

Bu pencereyi kaçırırsanız yeniden başlatma imkansız!

BAŞLATMA STRATEJİSİ:
━━━━━━━━━━━━━━━━━━━
1. Xenon'un düşmeye başladığı anı bekleyin
2. Çubukları dikkatlice çekin
3. ORM geq 15 tutun
4. Güç arttıkça xenon burn-out başlar

UYARI: ORM < 10 → ANLIK SCRAM gerekir!
            """.trimIndent()
        ),

        LevelInfo(
            number = 8, title = "Buhar Separatörü Seviye Kontrolü",
            subtitle = "Yük Değişiminde Tambur Seviyelerini Yönetin",
            difficulty = LevelInfo.Difficulty.MEDIUM,
            description = "Ani yük değişimi sırasında tambur seviyelerini kontrol edin.",
            objectives = listOf(
                "Şebeke talebi: %100 → %40 → %100 güç geçişi",
                "Tambur-1 ve Tambur-2 seviyelerini %20-80 içinde tutun",
                "Besleme suyu setpointini güce göre ayarlayın",
                "Buhar basıncını 6.0-7.5 MPa içinde tutun",
                "Tambur taşması veya boşalması olmasın"
            ),
            hints = listOf(
                "Güç düşünce buhar üretimi azalır → tambur seviyesi yükselir",
                "Hızlı güç düşüşünde besleme suyu setpointini azaltın",
                "Güç artarken besleme suyu artırın",
                "İki tambur aynı anda izlenmeli"
            ),
            briefing = """
BUHAR SEPARATÖRÜ KONTROLÜ — Seviye 8

Buhar separatörleri soğutucu-buhar karışımını ayırır.

SEVİYE EFEKTLERİ:
━━━━━━━━━━━━━━━━━
Seviye çok düşük (<10%): Su yetersiz → pompa kavitasyonu
Seviye çok yüksek (>90%): Su türbine kaçar → türbin hasarı

YÜK DEĞİŞİMİ ETKİSİ:
━━━━━━━━━━━━━━━━━━━━
Ani güç düşüşü → buhar üretimi düşer → seviye YÜKSELIR
Ani güç artışı → buhar üretimi artar → seviye DÜŞER

Güç düşerken besleme suyu AZALT
Güç artarken besleme suyu ARTIR
            """.trimIndent()
        ),

        LevelInfo(
            number = 9, title = "Manuel Reaktör Kontrolü",
            subtitle = "AR Sistemi Kapalı — Sadece Siz Varsınız",
            difficulty = LevelInfo.Difficulty.MEDIUM,
            description = "Otomatik Regülasyon (AR) sistemi bakım için kapatıldı. 20 dakika manuel kontrol.",
            objectives = listOf(
                "AR sistemi kapalı — otomatik düzeltme yok",
                "%80 gücü ±%3 içinde tutun (2560 ± 96 MW)",
                "ORM geq 15 tutun",
                "20 dakika boyunca limitlerin içinde kalın",
                "Xenon dalgalanmalarına el ile tepki verin"
            ),
            hints = listOf(
                "Xenon yaklaşık 10 dakikalık dalga periyoduna sahip",
                "Güç artarsa 1-2 çubuk sok, düşerse 1-2 çubuk çek",
                "Agresif hareket etme — küçük adımlar",
                "ORM göstergesini sürekli izle"
            ),
            briefing = """
MANUEL KONTROL — Seviye 9

AR sistemi bakım için kapalı.

MANUEL KONTROL STRATEJİSİ:
━━━━━━━━━━━━━━━━━━━━━━━━━━
Güç trendi: hangi yöne gidiyor? Önce bunu anlayın.
Küçük adımlar: aynı anda 1-2 çubuktan fazla hareket ettirmeyin.
Bekleyin: çubuk hareketinin etkisi 10-30 saniye içinde görünür.
Xenon: yaklasik sinüs dalgası, 10 dakika periyot.

XENON DALGALANMASI:
━━━━━━━━━━━━━━━━━━
Güç artarsa xenon yanar, reaktivite artar, daha çok güç.
Güç artınca çubuğu hemen sokun!

Güç düşerse xenon birikir, reaktivite düşer, daha az güç.
Güç düşünce çubuğu çekin ama aşırı değil!
            """.trimIndent()
        ),

        LevelInfo(
            number = 10, title = "İstasyon Karartması",
            subtitle = "Tam Şebeke Kaybı — Acil Sistemleri Devreye Al",
            difficulty = LevelInfo.Difficulty.HARD,
            isHard = true,
            description = "ZORG! Harici şebeke bağlantısı koptu. Dizel jeneratörleri başlatmalısınız.",
            objectives = listOf(
                "Şebeke kaybı tespiti: alarm geldi — hemen harekete geç",
                "Dizel jeneratörleri DERHAL başlatın",
                "AZ-5 ile kontrollü SCRAM başlatın",
                "SAOR soğutmasını devreye alın",
                "Soğutucu akışını sürdürün (en az 1500 kg/s)",
                "10 dakika boyunca çekirdek soğutmasını sağlayın"
            ),
            hints = listOf(
                "Şebeke gidince pompalar yavaşlamaya başlar!",
                "Türbin ataleti sadece 30-60 saniye elektrik verir",
                "Dizel başlatma: 10-15 saniye gecikme var",
                "AZ-5 basıldıktan sonra ECCS'i de devreye alın",
                "Soğutucu akışı < 500 kg/s → çekirdek hasarı başlar"
            ),
            briefing = """
İSTASYON KARARTMASI — Seviye 10 (ZORG)

Saat 00:28. Harici şebeke bağlantısı ani arıza nedeniyle kesildi.

KARARTMA PROTOKOLÜ:
━━━━━━━━━━━━━━━━━━
HEMEN (0-30 saniye):
1. Alarmı onayla
2. Dizel jeneratörleri başlat
3. AZ-5'e bas — kontrollü SCRAM

30-60 saniye:
4. Türbin ataleti düşüyor — pompalar yavaşlıyor
5. SAOR'u devreye al

60+ saniye:
6. Dizel devreye girdi — pompalar korunuyor
7. Soğutma sürdürülüyor

NEDEN KRİTİK:
━━━━━━━━━━━━
Reaktör kapalı olsa bile artık ısı üretiyor!
(Bozunma ısısı: kapatmadan sonra %5-7 güç)
Bu ısıyı sürekli soğutmak SARTTIR.
            """.trimIndent()
        ),

        LevelInfo(
            number = 11, title = "Xenon Zehirlenmesi Yönetimi",
            subtitle = "Yükselen Xenon — SCRAM mı, Devam mı?",
            difficulty = LevelInfo.Difficulty.HARD,
            description = "Xenon kontrolsüz yükseliyor, reaktiviteyi eritiyor.",
            objectives = listOf(
                "Xenon > 1.5: Kararı verin — sürdür veya kapat",
                "Devam edecekseniz: ORM geq 12 tutun",
                "Güç %50'nin altına düşürmeyin (xenon burn-out için)",
                "Reaktiviteyi pozitif tutmak için çubukları yönetin",
                "15 dakika boyunca kritikaliteyi sürdürün"
            ),
            hints = listOf(
                "Xenon düşürmenin tek yolu: gücü YÜKSEK tutmak",
                "Ama güç yüksek tutmak için ORM harcıyorsunuz",
                "ORM < 7 → SCRAM zorunlu!",
                "Şüpheye düştüğünüzde kapatın"
            ),
            briefing = """
XENON ZEHİRLENMESİ YÖNETİMİ — Seviye 11

Xenon-135 birikimi reaktiviteyi yiyor.

SEÇENEK A — GÜVENLİ KAPATMA:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
AZ-5 bas → SCRAM → 24 saat bekle → xenon çözülür
Risk: Düşük. Maliyet: Üretim kaybı.

SEÇENEK B — XENON BURN-OUT:
━━━━━━━━━━━━━━━━━━━━━━━━━━
Gücü yüksek tut → nötronlar xenon'u yakar → xenon azalır
Risk: Yüksek (ORM düşer, çubuklar çekilir).

ÖNEMLİ NOT:
━━━━━━━━━━
Tarihin en büyük nükleer felaketinde operatörler
xenon burn-out yapmak için ORM'u çok düşürdüler.
Siz daha akıllı olabilir misiniz?
            """.trimIndent()
        ),

        LevelInfo(
            number = 12, title = "Pozitif Boşluk Katsayısı",
            subtitle = "RBMK'nın En Tehlikeli Özelliği",
            difficulty = LevelInfo.Difficulty.HARD,
            description = "Soğutucu akışı azalıyor. Void fraksiyonu yükseliyor.",
            objectives = listOf(
                "Void fraksiyonu < %40 tutun",
                "Soğutucu akışı düşünce gücü hemen azaltın",
                "Güç/void geri besleme döngüsünü kırın",
                "Ek pompaları devreye alarak akışı artırın",
                "10 dakika boyunca sistemi stabilize edin"
            ),
            hints = listOf(
                "Void artışı = reaktivite artışı = güç artışı = daha fazla void (DÖNGÜ!)",
                "Bu döngüyü kırmak için: çubukları sok + akışı artır",
                "Void > %60 ise kontrolü kaybedebilirsiniz",
                "Düşük güçte void katsayısı daha da pozitif olur"
            ),
            briefing = """
POZİTİF BOŞLUK KATSAYISI — Seviye 12

RBMK reaktörlerinin en tehlikeli özelliği:
soğutucu kaynarsa reaktivite ARTAR.

POZİTİF GERİ BESLEME DÖNGÜSÜ:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Azalan akış → kaynama → void artar → Reaktivite ARTAR
→ Güç ARTAR → Daha fazla kaynama...

Bu kısır döngüyü kırmak için:
1. HEMEN çubukları sok
2. Akışı artır
3. Gücü düşür

DÜŞÜK GÜÇ UYARISI:
━━━━━━━━━━━━━━━━━
Güç %20'nin altında void katsayısı 2.5x daha büyük!
            """.trimIndent()
        ),

        LevelInfo(
            number = 13, title = "Çoklu Sistem Arızası",
            subtitle = "Aynı Anda Üç Sorun — Öncelik Belirleyin",
            difficulty = LevelInfo.Difficulty.HARD,
            description = "Sıralı arızalar: pompa, tambur, türbin. Hepsini yönetin.",
            objectives = listOf(
                "GTs-2 arızası: güç azalt",
                "BS-1 seviye düşüklüğü: besleme suyu artır",
                "TG-1 trip: buhar fazlası yönet",
                "SCRAM YAPMADAN tüm sistemleri stabilize et",
                "20 dakika boyunca tüm parametreler limitlerin içinde"
            ),
            hints = listOf(
                "Öncelik: soğutma > basınç > seviye > güç",
                "Birden fazla alarm aynı anda — paniğe kapılmayın",
                "Her sistemi sırayla düzeltin",
                "ORM göstergesini hiç gözden kaçırmayın"
            ),
            briefing = """
ÇOKLU SİSTEM ARIZASI — Seviye 13

Gerçek reaktör operasyonunda nadiren tek sorun çıkar.

ARIZA SIRASI:
━━━━━━━━━━━━
T+0s : GTs-2 trip → soğutucu akışı %12 düşer
T+45s: BS-1 seviyesi düşüyor → besleme suyu yetersiz
T+90s: TG-1 trip → buhar basıncı yükseliyor

ÖNCELİK SIRASI:
━━━━━━━━━━━━━━
1. SOĞUTMA  — Yakıt hasarını önle
2. BASINÇ   — Patlama önle
3. SEVİYELER — Pompa/türbin hasarı önle
4. GÜÇ     — Verimlilik

Eğer durum kontrolsüz geliyorsa SCRAM'dan çekinmeyin.
            """.trimIndent()
        ),

        LevelInfo(
            number = 14, title = "SKALA Bilgisayar Arızası",
            subtitle = "Dijital Göstergeler Dondu — Analog ile Çalışın",
            difficulty = LevelInfo.Difficulty.HARD,
            description = "SKALA reaktör bilgisayarı çöktü. Dijital göstergeler dondu.",
            objectives = listOf(
                "SKALA kapalı: Dijital göstergeler donmuş (güvenilmez)",
                "Analog göstergelerden gerçek değerleri okuyun",
                "ORM'u manuel hesaplayın (çubuk sayımı)",
                "30 dakika boyunca reaktörü güvenli tutun",
                "SKALA geri gelene kadar idare edin"
            ),
            hints = listOf(
                "Analog göstergeler: gerçek; dijital: donmuş (güvenilmez)",
                "ORM = içeride olan çubuk sayısı",
                "Log paneli hâlâ çalışıyor — onu izleyin",
                "Şüphe durumunda muhafazakar davranın"
            ),
            briefing = """
SKALA BİLGİSAYAR ARIZASI — Seviye 14

SKALA coktu. Dijital ekranlar donmus durumda — guvenilmez!

MANUEL OPERASYON:
━━━━━━━━━━━━━━━━
Güc     : Analog notron akimi olcer okuyun
Sicaklik: Analog termometre gostergeleri
Basinc  : Manometre ibrelerini izleyin
ORM     : Cubuk pozisyon tabelasini sayın

GÜVENLİK MOTTOsu:
━━━━━━━━━━━━━━━━
Süphede kal, muhafazakar davran.
Parametreler hakkinda emin degilseniz
gücu düsürün veya SCRAM yapın.
            """.trimIndent()
        ),

        LevelInfo(
            number = 15, title = "Yakıt Kanalı Patlaması",
            subtitle = "Birincil Devre Yırtılması — Acil Soğutma!",
            difficulty = LevelInfo.Difficulty.EXTREME,
            isHard = true,
            description = "ZORG! Bir yakıt kanalı patladı. Birincil devre basıncı düşüyor.",
            objectives = listOf(
                "Yakıt kanalı patlamasını tespit et (alarm + basınç düşüşü)",
                "AZ-5 ile ANINDA SCRAM yap",
                "SAOR (Acil Soğutma) sistemini devreye al",
                "Soğutucu basıncını izle",
                "Yakıt sıcaklığını < 1000°C tutun",
                "5 dakika soğutmayı sürdür"
            ),
            hints = listOf(
                "Yakıt kanalı patlaması = büyük soğutucu kaybı",
                "SCRAM + SAOR kombinasyonu şart",
                "SAOR suyu borulara enjekte eder",
                "Soğutucu basıncı < 4 MPa → SAOR otomatik devreye girer"
            ),
            briefing = """
YAKIT KANALI PATLAMASI — Seviye 15 (ZORG)

En ciddi reaktör kazalarından biri: birincil devre sızıntısı.

TESPİT BELİRTİLERİ:
━━━━━━━━━━━━━━━━━━━
RAZRYV TK alarmi
Sogutucu basinci düşüyor (6.9 → 4.0 MPa)
Sogutucu akisi artiyor (sizinti)

ACİL YANIT (ilk 30 saniye):
━━━━━━━━━━━━━━━━━━━━━━━━━━
1. AZ-5 → SCRAM
2. SAOR aktivasyonu
3. Türbinleri trip et
4. Tüm besleme sistemlerini izole et

SAOR (AVARIJNAYA SISTEMA OHLAZHDENIYA REAKTORA):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Acil soğutma sistemi çekirdek hasarını önler.
Basınç < 4 MPa: otomatik devreye girer.
Manuel de aktive edilebilir.
            """.trimIndent()
        ),

        LevelInfo(
            number = 16, title = "Gece Testi — 26 Nisan",
            subtitle = "Kariyer Zirveniz ve Kaçınılmaz Son",
            difficulty = LevelInfo.Difficulty.SCRIPTED,
            isFinal = true,
            description = "Terfi için son test. Türbin coast-down testini yapın. Ama bu reaktörün kaderi yazılmış...",
            objectives = listOf(
                "Gücü %100'den %22'ye düşürün",
                "ORM geq 15 tutmaya çalışın",
                "Türbin-1 coast-down testini başlatın",
                "Sonrasında ne olacağını izleyin...",
                "Bu sefer hiçbir şey kurtaramaz"
            ),
            hints = listOf(
                "Bu senaryo scripted — patlama kaçınılmaz",
                "Gerçek Çernobil senaryosunu yaşayacaksınız",
                "AZ-5'e basın — ama çok geç...",
                "Grafit uç etkisi geri döndürülemez"
            ),
            briefing = """
26 NİSAN — SON GÖREV

Aleksandr Bryuhanov olarak 15 seviyeyi başarıyla tamamladınız.
Baş Mühendis unvanı için son testiniz bu gece yapılacak.

GÖREV ÖZETI:
━━━━━━━━━━━━
Türbin-1'in coast-down testini yapmanız gerekiyor.

TEST PROSEDÜRü:
━━━━━━━━━━━━━━
1. Gücü %100'den %25'e düşürün
2. Türbin-1'i şebekeden ayırın
3. Pompaların türbin ataleti ile çalışmasını kaydedin
4. 45+ saniye veri toplayın
5. Gücü tekrar artırın

UYARI — GERÇEKÇİ SENARYO:
━━━━━━━━━━━━━━━━━━━━━━━━━
Bu senaryo gerçek Çernobil olayına dayanmaktadır.
Güç xenon nedeniyle %1'e düştü.
ORM tehlikeli seviyelere indi.
Grafit uç etkisi AZ-5'i silaha dönüştürdü.

01:23:40 — AZ-5 basıldı.
01:23:44 — Reaktör gücü 10 saniyede 30.000 MW'a ulaştı.
01:23:45 — İlk buhar patlaması.
01:23:46 — İkinci nükleer patlama.

Sizi durduracak hiçbir şey yok.
Bu tarihin en büyük nükleer felaketinin hikayesidir.
            """.trimIndent()
        )
    )

    fun getLevel(number: Int): LevelInfo? = ALL.find { it.number == number }

    fun isLevelUnlocked(number: Int, completedLevels: Set<Int>): Boolean {
        if (number == 1) return true
        return (number - 1) in completedLevels
    }
}
