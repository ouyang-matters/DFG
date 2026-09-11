package io.github.ouyangmatters.rounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** No DI framework here, just a small factory for building view models. */
class SimpleViewModelFactory(private val builder: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = builder() as T
}
