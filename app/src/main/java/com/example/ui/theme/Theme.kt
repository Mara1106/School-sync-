package com.example.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeModeOption
import com.example.data.model.ThemePaletteOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

data class ThemePreferencesState(
  val mode: ThemeModeOption = ThemeModeOption.SUGGESTED,
  val palette: ThemePaletteOption = ThemePaletteOption.INDIGO,
  val manualAccentHex: String = "#1E3A5F",
)

data class SmartThemeSuggestion(
  val recommendedMode: ThemeModeOption,
  val recommendedPalette: ThemePaletteOption,
  val reason: String,
)

object ThemePreferencesStore {
  private const val PREFS_NAME = "studysync_theme"
  private const val KEY_MODE = "theme_mode"
  private const val KEY_PALETTE = "theme_palette"
  private const val KEY_ACCENT = "manual_accent_hex"

  private val _state = MutableStateFlow(ThemePreferencesState())
  val state: StateFlow<ThemePreferencesState> = _state.asStateFlow()

  fun initialize(context: Context) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    _state.value =
      ThemePreferencesState(
        mode = ThemeModeOption.fromWire(prefs.getString(KEY_MODE, ThemeModeOption.SUGGESTED.wireValue).orEmpty()),
        palette = ThemePaletteOption.fromWire(prefs.getString(KEY_PALETTE, ThemePaletteOption.INDIGO.wireValue).orEmpty()),
        manualAccentHex = prefs.getString(KEY_ACCENT, "#1E3A5F").orEmpty().ifBlank { "#1E3A5F" },
      )
  }

  fun update(
    context: Context,
    mode: ThemeModeOption = _state.value.mode,
    palette: ThemePaletteOption = _state.value.palette,
    manualAccentHex: String = _state.value.manualAccentHex,
  ) {
    val normalizedHex = manualAccentHex.ifBlank { "#1E3A5F" }
    val next = ThemePreferencesState(mode = mode, palette = palette, manualAccentHex = normalizedHex)
    _state.value = next
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_MODE, mode.wireValue)
      .putString(KEY_PALETTE, palette.wireValue)
      .putString(KEY_ACCENT, normalizedHex)
      .apply()
  }

  fun smartSuggestionForHour(
    hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
  ): SmartThemeSuggestion {
    return if (hourOfDay in 6..17) {
      SmartThemeSuggestion(
        recommendedMode = ThemeModeOption.LIGHT,
        recommendedPalette = ThemePaletteOption.INDIGO,
        reason = "Daytime focus: Warm Academic Paper + Deep Indigo maximizes daylight contrast.",
      )
    } else {
      SmartThemeSuggestion(
        recommendedMode = ThemeModeOption.DARK,
        recommendedPalette = ThemePaletteOption.EMERALD,
        reason = "Evening study: Midnight Slate + Forest Teal reduces glare for late-night reading.",
      )
    }
  }
}

private fun parseSafeColor(hex: String, fallback: Color): Color =
  runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

private fun buildLightScheme(palette: ThemePaletteOption, manualAccentHex: String): ColorScheme {
  val primaryColor =
    when (palette) {
      ThemePaletteOption.INDIGO -> AcademicIndigo
      ThemePaletteOption.EMERALD -> Color(0xFF0F766E)
      ThemePaletteOption.SUNSET -> Color(0xFFB45309)
      ThemePaletteOption.VIOLET -> Color(0xFF6D28D9)
      ThemePaletteOption.MANUAL -> parseSafeColor(manualAccentHex, AcademicIndigo)
    }
  val secondaryColor =
    when (palette) {
      ThemePaletteOption.INDIGO -> AmberGold
      ThemePaletteOption.EMERALD -> Color(0xFF059669)
      ThemePaletteOption.SUNSET -> Color(0xFFDC2626)
      ThemePaletteOption.VIOLET -> SageTeal
      ThemePaletteOption.MANUAL -> lerp(primaryColor, AmberGold, 0.45f)
    }
  val primaryContainerColor = lerp(primaryColor, Color.White, 0.82f)
  val onPrimaryContainerColor = lerp(primaryColor, Color.Black, 0.45f)
  val secondaryContainerColor = lerp(secondaryColor, Color.White, 0.84f)
  val onSecondaryContainerColor = lerp(secondaryColor, Color.Black, 0.48f)

  return lightColorScheme(
    primary = primaryColor,
    onPrimary = CrispAlabaster,
    primaryContainer = primaryContainerColor,
    onPrimaryContainer = onPrimaryContainerColor,
    secondary = secondaryColor,
    onSecondary = CrispAlabaster,
    secondaryContainer = secondaryContainerColor,
    onSecondaryContainer = onSecondaryContainerColor,
    tertiary = SageTeal,
    onTertiary = CrispAlabaster,
    tertiaryContainer = SageTealContainer,
    onTertiaryContainer = OnSageTealContainer,
    background = IvoryPaper,
    onBackground = InkPrimary,
    surface = CrispAlabaster,
    onSurface = InkPrimary,
    surfaceVariant = WarmParchment,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = CrispAlabaster,
    surfaceContainerLow = IvoryPaper,
    surfaceContainer = WarmParchment,
    surfaceContainerHigh = SoftStone,
    outline = HairlineBorder,
    error = CrimsonCoral,
    onError = CrispAlabaster,
    errorContainer = CrimsonCoralContainer,
    onErrorContainer = OnCrimsonCoralContainer,
  )
}

private fun buildDarkScheme(palette: ThemePaletteOption, manualAccentHex: String): ColorScheme {
  val basePrimary =
    when (palette) {
      ThemePaletteOption.INDIGO -> SkyIndigo
      ThemePaletteOption.EMERALD -> Color(0xFF5EEAD4)
      ThemePaletteOption.SUNSET -> Color(0xFFFBBF24)
      ThemePaletteOption.VIOLET -> Color(0xFFC4B5FD)
      ThemePaletteOption.MANUAL -> lerp(parseSafeColor(manualAccentHex, SkyIndigo), Color.White, 0.48f)
    }
  val baseContainer =
    when (palette) {
      ThemePaletteOption.INDIGO -> SkyIndigoContainer
      ThemePaletteOption.EMERALD -> Color(0xFF115E59)
      ThemePaletteOption.SUNSET -> Color(0xFF78350F)
      ThemePaletteOption.VIOLET -> Color(0xFF4C1D95)
      ThemePaletteOption.MANUAL -> lerp(parseSafeColor(manualAccentHex, SkyIndigo), MidnightSlate, 0.5f)
    }
  val secondaryColor =
    when (palette) {
      ThemePaletteOption.INDIGO -> WarmGoldDark
      ThemePaletteOption.EMERALD -> MintTealDark
      ThemePaletteOption.SUNSET -> Color(0xFFFB7185)
      ThemePaletteOption.VIOLET -> MintTealDark
      ThemePaletteOption.MANUAL -> WarmGoldDark
    }

  return darkColorScheme(
    primary = basePrimary,
    onPrimary = Color(0xFF0A1D36),
    primaryContainer = baseContainer,
    onPrimaryContainer = Color(0xFFE2E8F0),
    secondary = secondaryColor,
    onSecondary = Color(0xFF2E1500),
    secondaryContainer = WarmGoldContainerDark,
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = MintTealDark,
    onTertiary = Color(0xFF042F2E),
    tertiaryContainer = MintTealContainerDark,
    onTertiaryContainer = Color(0xFF99F6E4),
    background = MidnightSlate,
    onBackground = SlateInk,
    surface = ElevatedSlate,
    onSurface = SlateInk,
    surfaceVariant = CardSlate,
    onSurfaceVariant = SlateSecondary,
    surfaceContainerLowest = MidnightSlate,
    surfaceContainerLow = ElevatedSlate,
    surfaceContainer = CardSlate,
    surfaceContainerHigh = Color(0xFF334155),
    outline = SlateBorder,
    error = CoralDark,
    onError = Color(0xFF450A0A),
    errorContainer = CoralContainerDark,
    onErrorContainer = Color(0xFFFECACA),
  )
}

val StudySyncShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun StudySyncTheme(
  themeState: ThemePreferencesState = ThemePreferencesState(),
  content: @Composable () -> Unit,
) {
  val systemDark = isSystemInDarkTheme()
  val isDark =
    when (themeState.mode) {
      ThemeModeOption.SUGGESTED -> systemDark
      ThemeModeOption.LIGHT -> false
      ThemeModeOption.DARK -> true
    }

  val colorScheme =
    if (isDark) buildDarkScheme(themeState.palette, themeState.manualAccentHex)
    else buildLightScheme(themeState.palette, themeState.manualAccentHex)

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = StudySyncShapes,
    content = content,
  )
}
