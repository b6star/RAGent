package com.yourssu.ragent.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.R

enum class AppIcon { Plus, Github, Docs, Repository, Members, Agent, ChatList, ChatEmpty, Back, More }
private val iconSize = 20.dp

@Composable
fun RAGentIcon(icon: AppIcon, color: Color, modifier: Modifier = Modifier) {
    @Composable
    fun DrawableIcon(resId: Int) = Icon (
        painter = painterResource(resId),
        contentDescription = null,
        modifier = modifier.size(iconSize),
        tint = color
    )
    @Composable
    fun VectorIcon(imageVector: ImageVector) = Icon (
        imageVector  = imageVector,
        contentDescription = null,
        modifier = modifier.size(iconSize),
        tint = color
    )
    when (icon) {
        AppIcon.Plus -> VectorIcon(Icons.Default.AddCircle)
        AppIcon.Back -> VectorIcon(Icons.Default.ArrowBackIosNew)
        AppIcon.More -> VectorIcon(Icons.Default.MoreHoriz)
        AppIcon.Repository -> DrawableIcon(R.drawable.ic_repository)
        AppIcon.Docs -> DrawableIcon(R.drawable.ic_docs)
        AppIcon.Agent -> DrawableIcon(R.drawable.ic_agent)
        AppIcon.Github -> DrawableIcon(R.drawable.ic_github)
        AppIcon.Members -> DrawableIcon(R.drawable.ic_members)
        AppIcon.ChatList -> DrawableIcon(R.drawable.ic_chat_list)
        AppIcon.ChatEmpty -> DrawableIcon(R.drawable.ic_chat_empty)
    }
}



