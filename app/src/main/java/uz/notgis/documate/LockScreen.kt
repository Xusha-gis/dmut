package uz.notgis.documate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LockGate(vm: AppViewModel, t: L, content: @Composable () -> Unit) {
    var unlocked by remember { mutableStateOf(false) }
    var needLock by remember { mutableStateOf<Boolean?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // settings oqimidan bir marta o'qish
        vm.settings.collect { s ->
            if (s.loaded) {
                needLock = s.lockOn && s.hasPin
                if (!(s.lockOn && s.hasPin)) unlocked = true
            }
        }
    }
    if (unlocked || needLock == false) {
        content()
        return
    }
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(t.unlockTitle, style = MaterialTheme.typography.titleLarge)
        Text(t.unlockHint, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { pin = it; err = false } },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (pin.length >= 4) {
                    busy = true
                    scope.launch {
                        unlocked = vm.verifyPin(pin)
                        err = !unlocked
                        busy = false
                    }
                }
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (err) Text(t.pinWrong, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        Button(
            onClick = {
                busy = true
                scope.launch {
                    unlocked = vm.verifyPin(pin)
                    err = !unlocked
                    busy = false
                }
            },
            enabled = pin.length >= 4 && !busy,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        ) { Text(t.open) }
    }
}
