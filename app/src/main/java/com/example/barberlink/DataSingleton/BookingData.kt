package com.example.barberlink.DataSingleton

import BundlingPackage
import Employee
import Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object BookingData {
    val listCapster: MutableList<Employee> = mutableListOf()
    val listService: MutableList<Service> = mutableListOf()
    val listBundling: MutableList<BundlingPackage> = mutableListOf()

    // Functions for listCapster
    fun addCapster(item: Employee) {
        CoroutineScope(Dispatchers.Default).launch {
            if (!listCapster.contains(item)) {
                listCapster.add(item)
            }
        }
    }

    fun removeCapster(item: Employee) {
        CoroutineScope(Dispatchers.Default).launch {
            listCapster.remove(item)
        }
    }

    fun addAllCapster(items: List<Employee>, isReplaceAll: Boolean) {
        CoroutineScope(Dispatchers.Default).launch {
            if (isReplaceAll) {
                listCapster.clear()
                listCapster.addAll(items)
            } else items.forEach { addCapster(it) }
        }
    }

    fun removeAllCapster(items: List<Employee>) {
        CoroutineScope(Dispatchers.Default).launch {
            listCapster.removeAll(items.toSet())
        }
    }

    fun clearCapsters() {
        CoroutineScope(Dispatchers.Default).launch {
            listCapster.clear()
        }
    }

    // Functions for listService
    fun addService(item: Service) {
        CoroutineScope(Dispatchers.Default).launch {
            if (!listService.contains(item)) {
                listService.add(item)
            }
        }
    }

    fun removeService(item: Service) {
        CoroutineScope(Dispatchers.Default).launch {
            listService.remove(item)
        }
    }

    fun addAllService(items: List<Service>, isReplaceAll: Boolean) {
        CoroutineScope(Dispatchers.Default).launch {
            if (isReplaceAll) {
                listService.clear()
                listService.addAll(items)
            } else items.forEach { addService(it) }
        }
    }

    fun removeAllService(items: List<Service>) {
        CoroutineScope(Dispatchers.Default).launch {
            listService.removeAll(items.toSet())
        }
    }

    fun clearServices() {
        CoroutineScope(Dispatchers.Default).launch {
            listService.clear()
        }
    }

    // Functions for listBundling
    fun addBundling(item: BundlingPackage) {
        CoroutineScope(Dispatchers.Default).launch {
            if (!listBundling.contains(item)) {
                listBundling.add(item)
            }
        }
    }

    fun removeBundling(item: BundlingPackage) {
        CoroutineScope(Dispatchers.Default).launch {
            listBundling.remove(item)
        }
    }

    fun addAllBundling(items: List<BundlingPackage>, isReplaceAll: Boolean) {
        CoroutineScope(Dispatchers.Default).launch {
            if (isReplaceAll) {
                listBundling.clear()
                listBundling.addAll(items)
            } else items.forEach { addBundling(it) }
        }
    }

    fun removeAllBundling(items: List<BundlingPackage>) {
        CoroutineScope(Dispatchers.Default).launch {
            listBundling.removeAll(items.toSet())
        }
    }

    fun clearBundlings() {
        CoroutineScope(Dispatchers.Default).launch {
            listBundling.clear()
        }
    }
}

