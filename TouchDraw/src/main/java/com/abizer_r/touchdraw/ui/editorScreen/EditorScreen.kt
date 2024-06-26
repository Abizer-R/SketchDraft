package com.abizer_r.touchdraw.ui.editorScreen

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abizer_r.components.R
import com.abizer_r.components.theme.SketchDraftTheme
import com.abizer_r.components.util.ImmutableList
import com.abizer_r.touchdraw.ui.editorScreen.bottomToolbar.BottomToolBarStatic
import com.abizer_r.touchdraw.ui.editorScreen.bottomToolbar.state.BottomToolbarEvent
import com.abizer_r.touchdraw.ui.editorScreen.bottomToolbar.state.BottomToolbarItem
import com.abizer_r.touchdraw.ui.editorScreen.topToolbar.TopToolBar
import com.abizer_r.touchdraw.ui.effectsMode.EffectsModeScreen
import com.abizer_r.touchdraw.utils.SharedTransitionPreviewExtension
import com.abizer_r.touchdraw.utils.editorScreen.EditorScreenUtils
import com.abizer_r.touchdraw.utils.other.bitmap.ImmutableBitmap

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.EditorScreen(
    modifier: Modifier = Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope,
    initialEditorScreenState: EditorScreenState,
    goToDrawModeScreen: (finalEditorState: EditorScreenState) -> Unit,
    goToTextModeScreen: (finalEditorState: EditorScreenState) -> Unit,
    goToEffectsModeScreen: (finalEditorState: EditorScreenState) -> Unit,
) {
    if (initialEditorScreenState.bitmapStack.isEmpty()) {
        throw Exception("EmptyStackException: The bitmapStack of initial state should contain at least one bitmap")
    }

    val context = LocalContext.current
    val lifeCycleOwner = LocalLifecycleOwner.current

    val viewModel: EditorScreenViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle(
        lifecycleOwner = lifeCycleOwner
    )

    LaunchedEffect(key1 = Unit) {
        viewModel.updateInitialState(initialEditorScreenState)
    }

    if (state.bitmapStack.isNotEmpty()) {
        // Adding this check because the default state in viewModel will have empty stack
        // After updating the initialEditorScreenState, we will have non-empty stack
        val currentBitmap = viewModel.getCurrentBitmap()

        val onBottomToolbarEvent = remember<(BottomToolbarEvent) -> Unit> {{ toolbarEvent ->
            if (toolbarEvent is BottomToolbarEvent.OnItemClicked) {
                when (toolbarEvent.toolbarItem) {
                    BottomToolbarItem.DrawMode -> {
                        goToDrawModeScreen(state)
                    }
                    BottomToolbarItem.TextMode -> {
                        goToTextModeScreen(state)
                    }
                    BottomToolbarItem.EffectsMode -> {
                        goToEffectsModeScreen(state)
                    }
                    else -> {}
                }
            }
        }}

        EditorScreenLayout(
            modifier = modifier,
            animatedVisibilityScope = animatedVisibilityScope,
            currentBitmap = currentBitmap,
            undoEnabled = viewModel.undoEnabled(),
            redoEnabled = viewModel.redoEnabled(),
            onUndo = viewModel::onUndo,
            onRedo = viewModel::onRedo,
            onBottomToolbarEvent = onBottomToolbarEvent
        )
    }

}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.EditorScreenLayout(
    modifier: Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope,
    currentBitmap: Bitmap,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBottomToolbarEvent: (BottomToolbarEvent) -> Unit
) {

    val bottomToolbarItems = remember {
        ImmutableList(EditorScreenUtils.getDefaultBottomToolbarItemsList())
    }

    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val (topToolbar, bottomToolbar, bgImage) = createRefs()

        TopToolBar(
            modifier = Modifier.constrainAs(topToolbar) {
                top.linkTo(parent.top)
                width = Dimension.matchParent
                height = Dimension.wrapContent
            },
            undoEnabled = undoEnabled,
            redoEnabled = redoEnabled,
            showCloseAndDone = false,
            onUndo = onUndo,
            onRedo = onRedo
        )

        val aspectRatio = currentBitmap?.let {
            currentBitmap.width.toFloat() / currentBitmap.height.toFloat()
        }
        val screenShotBoxWidth = if (aspectRatio != null) {
            Dimension.ratio(aspectRatio.toString())
        } else Dimension.fillToConstraints
        Box(
            modifier = Modifier
                .constrainAs(bgImage) {
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    top.linkTo(topToolbar.bottom)
                    bottom.linkTo(bottomToolbar.top)
                    width = screenShotBoxWidth
                    height = Dimension.fillToConstraints
                }
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "centerImageBound"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    boundsTransform = { _, _ ->
                        tween(durationMillis = 400)
                    }
                )
        ) {

            Image(
                modifier = Modifier
                    .fillMaxSize(),
                bitmap = currentBitmap.asImageBitmap(),
                contentScale = ContentScale.Fit,
                contentDescription = null,
                alpha = 1f
            )
        }


        BottomToolBarStatic(
            modifier = Modifier.constrainAs(bottomToolbar) {
                bottom.linkTo(parent.bottom)
                width = Dimension.matchParent
                height = Dimension.wrapContent
            },
            toolbarItems = bottomToolbarItems,
            onEvent = onBottomToolbarEvent
        )


    }
}



@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewEditorScreen() {
    SketchDraftTheme {
        SharedTransitionPreviewExtension {
            EditorScreenLayout(
                modifier = Modifier,
                animatedVisibilityScope = it,
                currentBitmap = ImageBitmap.imageResource(id = R.drawable.placeholder_image_2).asAndroidBitmap(),
                undoEnabled = false,
                redoEnabled = false,
                onUndo = {},
                onRedo = {},
                onBottomToolbarEvent = {}
            )

        }

    }
}