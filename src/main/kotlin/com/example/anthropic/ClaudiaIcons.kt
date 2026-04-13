package com.example.anthropic

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object ClaudiaIcons {
    @JvmField
    val Sessions: Icon = IconLoader.getIcon("/icons/claudiaSessions.svg", ClaudiaIcons::class.java)

    @JvmField
    val Memory: Icon = IconLoader.getIcon("/icons/claudiaMemory.svg", ClaudiaIcons::class.java)
}
