package com.example.marketplace.util

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.text.Normalizer

/** Remove acentos e caixa para que "cafe" encontre "Café" */
fun String.normalizarBusca(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .trim()

/** true se todas as palavras da [consulta] aparecem em algum dos [campos] */
fun correspondeBusca(consulta: String, vararg campos: String?): Boolean {
    val palavras = consulta.normalizarBusca().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (palavras.isEmpty()) return true
    val texto = campos.filterNotNull().joinToString(" ").normalizarBusca()
    return palavras.all { texto.contains(it) }
}

/** Campo de pesquisa padrão usado nas telas que listam produtos ou pedidos */
@Composable
fun CampoBusca(
    valor: String,
    onValorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Buscar produtos"
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = valor,
        onValueChange = onValorChange,
        modifier = modifier,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (valor.isNotEmpty()) {
                IconButton(onClick = { onValorChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}
