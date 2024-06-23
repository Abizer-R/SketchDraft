package com.abizer_r.touchdraw.ui.textMode

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.abizer_r.components.util.ImmutableList
import com.abizer_r.touchdraw.utils.textMode.blurBackground.BlurBitmapBackground
import com.abizer_r.components.util.defaultErrorToast
import com.abizer_r.touchdraw.ui.textMode.TextModeEvent.*
import com.abizer_r.touchdraw.ui.editorScreen.bottomToolbar.BottomToolBar
import com.abizer_r.touchdraw.ui.editorScreen.bottomToolbar.state.BottomToolbarEvent
import com.abizer_r.touchdraw.ui.editorScreen.topToolbar.TextModeTopToolbar
import com.abizer_r.touchdraw.ui.textMode.textEditorLayout.TextEditorLayout
import com.abizer_r.touchdraw.ui.transformableViews.base.TransformableTextBoxState
import com.abizer_r.touchdraw.utils.other.bitmap.ImmutableBitmap
import com.abizer_r.touchdraw.utils.textMode.TextModeUtils
import com.abizer_r.touchdraw.utils.textMode.TextModeUtils.BorderForSelectedViews
import com.abizer_r.touchdraw.utils.textMode.TextModeUtils.DrawAllTransformableViews
import com.skydoves.cloudy.Cloudy
import com.smarttoolfactory.screenshot.ImageResult
import com.smarttoolfactory.screenshot.ScreenshotBox
import com.smarttoolfactory.screenshot.rememberScreenshotState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TextModeScreen(
    modifier: Modifier = Modifier,
    immutableBitmap: ImmutableBitmap,
    onDoneClicked: (bitmap: Bitmap) -> Unit,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val lifeCycleOwner = LocalLifecycleOwner.current

    val viewModel: TextModeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle(
        lifecycleOwner = lifeCycleOwner
    )

    val bottomToolbarItems = remember {
        ImmutableList(TextModeUtils.getDefaultBottomToolbarItemsList())
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(key1 = Unit) {
        onDispose {
            keyboardController?.hide()
        }
    }

    val screenshotState = rememberScreenshotState()

    when (screenshotState.imageState.value) {
        ImageResult.Initial -> {}
        is ImageResult.Error -> {
            viewModel.shouldGoToNextScreen = false
            context.defaultErrorToast()
        }
        is ImageResult.Success -> {
            if (viewModel.shouldGoToNextScreen) {
                viewModel.shouldGoToNextScreen = false
                screenshotState.bitmap?.let { mBitmap ->
                    onDoneClicked(mBitmap)
                } ?: context.defaultErrorToast()
            }
        }
    }

    BackHandler {
        if (state.textFieldState.isVisible) {
            viewModel.onEvent(HideTextField)
        } else {
            onBackPressed()
        }
    }

    val defaultTextFont = MaterialTheme.typography.headlineMedium.fontSize
    LaunchedEffect(key1 = Unit) {
        viewModel.onEvent(
            TextModeEvent.UpdateTextFont(
                textFont = defaultTextFont
            )
        )
    }

    val onCloseClickedLambda = remember<() -> Unit> {{
        if (state.textFieldState.isVisible) {
            viewModel.onEvent(HideTextField)
        } else {
            onBackPressed()
        }
    }}

    val onDoneClickedLambda = remember<() -> Unit> {{
        if (state.textFieldState.isVisible) {
            viewModel.onEvent(AddTransformableTextBox(
                textBoxState = TransformableTextBoxState(
                    id = state.textFieldState.textStateId ?: UUID.randomUUID().toString(),
                    text = state.textFieldState.text,
                    textColor = state.textFieldState.getSelectedColor(),
                    textAlign = state.textFieldState.textAlign,
                    textFont = state.textFieldState.textFont
                )
            ))
            viewModel.onEvent(HideTextField)
        } else {
            viewModel.handleStateBeforeCaptureScreenshot()
            lifeCycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                delay(200)  /* Delay to update the selection in ui */
                screenshotState.capture()
            }
        }
    }}

    val onBgClickedLambda = remember<() -> Unit> {{
        viewModel.updateViewSelection(null)
    }}

    val onBottomToolbarEventLambda = remember<(BottomToolbarEvent) -> Unit> {{
        viewModel.onBottomToolbarEvent(it)
    }}


    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        val (topToolBar, bottomToolbar, editorBox, editorBoxBgStretched, textInputView) = createRefs()

        val isEditorVisibleAndBgBlurred = state.showBlurredBg.not() && state.textFieldState.isVisible.not()

        TextModeTopToolbar(
            modifier = Modifier.constrainAs(topToolBar) {
                top.linkTo(parent.top)
                width = Dimension.matchParent
                height = Dimension.wrapContent
            },
            onCloseClicked = onCloseClickedLambda,
            onDoneClicked = onDoneClickedLambda
        )
        val bitmap = immutableBitmap.bitmap
        val aspectRatio = bitmap.let {
            bitmap.width.toFloat() / bitmap.height.toFloat()
        }
        ScreenshotBox(
            modifier = Modifier
                .constrainAs(editorBox) {
                    top.linkTo(topToolBar.bottom)
                    bottom.linkTo(
                        if (isEditorVisibleAndBgBlurred) bottomToolbar.top
                        else parent.bottom
                    )
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.ratio(aspectRatio.toString())
                    height = Dimension.fillToConstraints
                }
                .clipToBounds(),
            screenshotState = screenshotState
        ) {

            BlurBitmapBackground(
                modifier = Modifier.fillMaxSize(),
                imageBitmap = bitmap.asImageBitmap(),
                shouldBlur = state.showBlurredBg,
                blurRadius = 15,
                onBgClicked = onBgClickedLambda
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (isEditorVisibleAndBgBlurred) {
                    DrawAllTransformableViews(
                        centerAlignModifier = Modifier.align(Alignment.Center),
                        transformableViewsList = state.transformableViewStateList,
                        onTransformableBoxEvent = {
                            viewModel.onTransformableBoxEvent(it)
                        }
                    )
                }
            }

        }

        Box(
            modifier = Modifier
                .constrainAs(editorBoxBgStretched) {
                    top.linkTo(topToolBar.bottom)
                    bottom.linkTo(bottomToolbar.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                    height = Dimension.fillToConstraints
                }
                .clipToBounds()
        ) {


            if (isEditorVisibleAndBgBlurred) {
                BorderForSelectedViews(
                    centerAlignModifier = Modifier.align(Alignment.Center),
                    transformableViewsList = state.transformableViewStateList,
                    onTransformableBoxEvent = {
                        viewModel.onTransformableBoxEvent(it)
                    }
                )
            }

        }

        if (state.textFieldState.isVisible) {
            TextEditorLayout(
                modifier = Modifier
                    .constrainAs(textInputView) {
                        top.linkTo(topToolBar.bottom)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        width = Dimension.fillToConstraints
                        height = Dimension.fillToConstraints
                    },
                textFieldState = state.textFieldState,
                onTextModeEvent = {
                    viewModel.onEvent(it)
                }
            )

        }


        if (isEditorVisibleAndBgBlurred) {
            BottomToolBar(
                modifier = Modifier.constrainAs(bottomToolbar) {
                    bottom.linkTo(parent.bottom)
                    width = Dimension.matchParent
                    height = Dimension.wrapContent
                },
                toolbarItems = bottomToolbarItems,
                onEvent = onBottomToolbarEventLambda
            )

        }
    }
}