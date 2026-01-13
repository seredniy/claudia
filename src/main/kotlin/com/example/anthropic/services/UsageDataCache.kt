package com.example.anthropic.services

import com.example.anthropic.api.models.UsageData
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class UsageDataCache {
    private val lock = ReentrantReadWriteLock()
    private var cachedData: UsageData? = null

    /**
     * Update the cached usage data
     */
    fun update(data: UsageData) {
        lock.write {
            cachedData = data
        }
    }

    /**
     * Get the cached usage data
     */
    fun get(): UsageData? {
        return lock.read {
            cachedData
        }
    }

    /**
     * Clear the cache
     */
    fun clear() {
        lock.write {
            cachedData = null
        }
    }

    /**
     * Check if cache has data
     */
    fun hasData(): Boolean {
        return lock.read {
            cachedData != null
        }
    }
}
