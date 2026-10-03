package io.maffinet.android.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import io.maffinet.android.ui.components.ProductScreen
import io.maffinet.android.ui.components.ProductSettingLink

@Composable
fun SettingsScreen(focusRequester: FocusRequester, onNavigate: (Int) -> Unit) {
    ProductScreen("Настройки", focusRequester, subtitle = "Основные настройки и дополнительные возможности.") {
        ProductSettingLink("Domain lists", "General, списки сервисов и ваши домены", { onNavigate(9) })
        ProductSettingLink("Приложение и подключение", "Автозапуск, батарея, выбор приложений, DNS, импорт и экспорт стратегий", { onNavigate(7) })
        ProductSettingLink("Advanced → ByeDPI", "Команда стратегии, host filtering, IPv6 и параметры десинхронизации", { onNavigate(8) })
        ProductSettingLink("Telegram-прокси", "Существующий локальный MTProto-прокси и его параметры", { onNavigate(1) })
        ProductSettingLink("Настройка YouTube и Android TV", "Ручная проверка видео, SmartTube и прежний мастер настройки", { onNavigate(6) })
        ProductSettingLink("О Maffinet", "Версия, исходники, лицензия и благодарности", { onNavigate(4) })
    }
}
