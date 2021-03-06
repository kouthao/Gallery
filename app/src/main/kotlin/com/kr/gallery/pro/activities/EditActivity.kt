package com.kr.gallery.pro.activities

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.Bitmap.CompressFormat
import android.graphics.Bitmap.createBitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.view.View.OnLongClickListener
import android.view.animation.*
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.Nullable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.kr.commons.dialogs.ColorPickerDialog
import com.kr.commons.extensions.*
import com.kr.commons.helpers.REAL_FILE_PATH
import com.kr.commons.helpers.RESULT_MEDIUM
import com.kr.commons.helpers.ensureBackgroundThread
import com.kr.commons.helpers.isNougatPlus
import com.kr.commons.models.FileDirItem
import com.kr.commons.views.MySeekBar
import com.kr.gallery.pro.R
import com.kr.gallery.pro.adapters.AdjustAdapter
import com.kr.gallery.pro.adapters.CropSelectorRVAdapter
import com.kr.gallery.pro.adapters.FiltersAdapter
import com.kr.gallery.pro.dialogs.SaveBeforeExitDialog
import com.kr.gallery.pro.dialogs.SaveChangesDialog
import com.kr.gallery.pro.extensions.config
import com.kr.gallery.pro.helpers.MEDIA_IMAGE
import com.kr.gallery.pro.helpers.Utils
import com.kr.gallery.pro.models.*
import com.kr.gallery.pro.models.FilterPack.Companion.getFilterPack
import com.kr.gallery.pro.views.*
import com.skydoves.powerspinner.OnSpinnerItemSelectedListener
import com.warkiz.widget.IndicatorSeekBar
import com.warkiz.widget.OnSeekChangeListener
import com.warkiz.widget.SeekParams
import com.yalantis.ucrop.util.FastBitmapDrawable
import com.yalantis.ucrop.util.RectUtils
import com.yalantis.ucrop.view.CropImageView
import com.yalantis.ucrop.view.CropImageView.FREE_ASPECT_RATIO
import com.yalantis.ucrop.view.TransformImageView.TransformImageListener
import doodle.*
import doodle.DoodleView.*
import kotlinx.android.synthetic.main.activity_edit.*
import kotlinx.android.synthetic.main.bottom_bar_container.*
import kotlinx.android.synthetic.main.bottom_close_check_bar.*
import kotlinx.android.synthetic.main.bottom_editor_adjust_actions.*
import kotlinx.android.synthetic.main.bottom_editor_crop_actions.*
import kotlinx.android.synthetic.main.bottom_editor_doodle_actions.*
import kotlinx.android.synthetic.main.bottom_editor_filter_actions.*
import kotlinx.android.synthetic.main.bottom_editor_more_actions.*
import kotlinx.android.synthetic.main.bottom_editor_mosaic_actions.*
import kotlinx.android.synthetic.main.bottom_editor_primary_actions.*
import kotlinx.android.synthetic.main.bottom_more_draw_color.*
import kotlinx.android.synthetic.main.bottom_more_draw_size.*
import kotlinx.android.synthetic.main.bottom_more_draw_style.*
import kotlinx.android.synthetic.main.doodle_bottom_size_color_bar.*
import kotlinx.android.synthetic.main.doodle_image_view.*
import kotlinx.android.synthetic.main.ucrop_view.*
import org.wysaid.nativePort.CGENativeLibrary
import org.wysaid.view.ImageGLSurfaceView
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.sqrt

/**
 * ????????????????????? activity
 */
class EditActivity : SimpleActivity(), CropImageView.OnCropImageCompleteListener, View.OnTouchListener {

    private val TAG = "EditActivity"

    private val ROTATE_WIDGET_SENSITIVITY_COEFFICIENT = 13f

    private val mInterpolator = LinearInterpolator()
    private val mAnimator = CustomValueAnimator(mInterpolator)

    private val CROP = "crop"

    // constants for bottom primary action groups
    private val PRIMARY_ACTION_CROP = 1
    private val PRIMARY_ACTION_FILTER = 2
    private val PRIMARY_ACTION_ADJUST = 3
    private val PRIMARY_ACTION_MORE = 4

    // More?????? doodle/mosaic/draw ?????? ????????? ???????????? ????????????
    private val DOODLE_ACTION_NONE = 5
    //More?????? doodle??? ???????????????
    private val DOODLE_ACTION_DOODLE = 6
    //More?????? mosaic??? ???????????????
    private val DOODLE_ACTION_MOSAIC = 7
    //More?????? draw??? ???????????????
    private val DOODLE_ACTION_DRAW = 8

    // Draw?????? ??????????????? ?????????
    // ?????????????????? ????????? ????????????
    private var MORE_DRAW_COLOR_0 = Color.RED;
    private var MORE_DRAW_COLOR_1 = Color.GREEN;
    private var MORE_DRAW_COLOR_2 = Color.BLUE;
    private var MORE_DRAW_COLOR_3 = Color.BLACK;
    private var MORE_DRAW_COLOR_4 = Color.WHITE;

    private val DOODLE_SIZE_1 = 4
    private val DOODLE_SIZE_2 = 12
    private val DOODLE_SIZE_3 = 25

    // Doodle?????? ??????????????? ?????????
    private val color_ary = arrayOf(
            "#ffffff", "#cccccc", "#808080", "#382e2d", "#000000", "#7a0400",
            "#cc0001", "#ff3737", "#9d3729", "#ff7f00", "#ffa33a", "#f7c200",
            "#a3e400", "#7caa0a", "#077c38", "#175445", "#7fd7fb", "#009afa",
            "#0265cb", "#001a65", "#3e0067", "#75008c", "#ff2e8c", "#febad3"
    )

    val DOODLE_MOSAIC_LEVEL = "doodle_mosaic_level"
    val DOODLE_MOSAIC_SIZE = "doodle_mosaic_size"

    private var realFilePath = ""
    // ???????????? ??????
    private lateinit var saveUri: Uri
    private var uri: Uri? = null
    // ?????? ????????? ????????? ???????????? ?????? ??????
    private var currPrimaryAction = PRIMARY_ACTION_CROP
    // ?????? ????????? Doodle??? ???????????? ?????? ??????
    private var currDoodleAction = DOODLE_ACTION_NONE
    // ?????? ????????? ?????????????????? ????????????. (-1, -1)??? ??????????????????
    private var currAspectRatio: Pair<Float, Float> = Pair(-1f, -1f)
    private var currMoreDrawStyle = MORE_DRAW_STYLE_RECT
    private var currMoreDrawColor = MORE_DRAW_COLOR_0
    private var currDoodleSizeSelection = DOODLE_SIZE_1
    // ???????????? ????????????????????? ??????????????? ??????
    private var isCropIntent = false

    private var oldExif: ExifInterface? = null
    // ??????????????? ????????? ?????? bitmap
    private var currentBitmap: Bitmap? = null
    // 6MP ????????? ????????? ????????????????????? ????????????
    private var originalBitmap: Bitmap? = null

    // Doodle
    val DEFAULT_MOSAIC_SIZE: Int = 30

    val RESULT_ERROR = -111

    private lateinit var mDoodle: IDoodle
    private lateinit var mDoodleView: DoodleView

    private lateinit var mDoodleParams: DoodleParams

    private lateinit var mTouchGestureListener: DoodleOnTouchGestureListener
    // ???????????? ????????? ?????? ??????
    private var mMosaicLevel = -1

    lateinit var sharedPreferences: SharedPreferences
    lateinit var editor: SharedPreferences.Editor
    // doodle?????? ??????????????? ??????????????? ?????? HashMap
    // ?????? ID??? ????????????
    private val mBtnColorIds: HashMap<Int,Int> = HashMap<Int,Int>()

    // ?????? ??????????????? ???????????? ?????? View. ??????????????? animation??? ????????? ??????
    lateinit var preBottomAccordian: View
    // ?????? ????????? ???????????? ????????? ??????
    var prePrimaryAction = -1;

    // Filter??? Adjust?????? ????????? ????????? Animation??? ?????? Handler
    private var text_alert_handler = Handler()

    private var CURVE_CHANNEL_RGB = "RGB";
    private var CURVE_CHANNEL_R = "R";
    private var CURVE_CHANNEL_G = "G";
    private var CURVE_CHANNEL_B = "B";

    private var currentRGBChannel = CURVE_CHANNEL_RGB;

    // RGB curve??? ????????? ?????? ????????????.
    private var curve_points_rgb = ArrayList<PointF>()
    private var curve_points_r = ArrayList<PointF>()
    private var curve_points_g = ArrayList<PointF>()
    private var curve_points_b = ArrayList<PointF>()

    // Adjust??? curve?????? ??????.
    // ?????????????????? ???????????? e.g. "RGB(123.3, 34.5)(54.3, 45.8)"
    private var color_points_rgb = ""
    // ?????????????????? ???????????? e.g. "R(123.3, 34.5)(54.3, 45.8)"
    private var color_points_r = ""
    // ?????????????????? ???????????? e.g. "G(123.3, 34.5)(54.3, 45.8)"
    private var color_points_g = ""
    // ?????????????????? ???????????? e.g. "B(123.3, 34.5)(54.3, 45.8)"
    private var color_points_b = ""

    private var historyManager = HistoryManager(this)

    private var isAdjustApplied = false

    // ???????????? ???????????? ???????????????????????? ???????????? ??????
    private val CROP_FOR_COMPARE = "crop_for_compare";
    // ??????????????? Filter??? ???????????? ??????
    private val CROP_FOR_FILTER = "crop_for_filter";
    // ??????????????? Adjust??? ???????????? ??????
    private val CROP_FOR_ADJUST = "crop_for_adjust";
    // ??????????????? More??? ???????????? ??????
    private val CROP_FOR_MORE = "crop_for_more";
    // ????????????????????? ????????? ???????????? ??????
    private val CROP_FOR_SAVE = "crop_for_save";

    // ????????? ???????????? ?????? ????????? ?????????????????? ????????????
    private var cropForAction = CROP_FOR_COMPARE;

    // ???????????? ?????????????????? ????????????
    private var mCropInProgress = false;

    // downsample??? ???????????? ?????? history??? ???????????? ??????
    private val ACTION_CROP = "action_crop";
    private val ACTION_FILTER = "action_filter";
    private val ACTION_ADJUST = "action_adjust";
    private val ACTION_DOODLE = "action_doodle";
    private val ACTION_MOSAIC = "action_mosaic";
    private val ACTION_DRAW = "action_draw";

    // downsample??? ???????????? ?????? history??? ??????, undo???????????? ??????????????? ??????
    private val appliedHistory = ArrayList<AppliedValueHistory>()
    // downsample??? ???????????? ?????? redo history??? ??????, undo?????? ????????????
    private val appliedRedoHistory = ArrayList<AppliedValueHistory>()

    // ??????????????? ???????????? ???????????? glSurfaceView
    private lateinit var adjust_view : ImageGLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (MyDebug.LOG) Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_edit)

        if (checkAppSideloading()) {
            return
        }

        initEditActivity()
    }

    private fun onDoodleReady() {
        // ?????? ?????????
        mDoodleView.clear()
        updateDoodleUndoRedo()

        // ???/??????/??????/??????/?????? ?????? ??????
        if (currDoodleAction == DOODLE_ACTION_DOODLE) {
            mDoodle.pen = DoodlePen.BRUSH
            loadPreference()
        } else if (currDoodleAction == DOODLE_ACTION_MOSAIC) {
            mDoodle.pen = DoodlePen.MOSAIC
            loadMosaicPreference()
        } else if(currDoodleAction == DOODLE_ACTION_DRAW) {
            mDoodle.pen = DoodlePen.BRUSH
            updateDrawThickness(config.lastEditorBrushSize)
            updateDrawStyle(config.lastEditorBrushStyle)
            updateDrawColor(config.lastEditorBrushColor)
        }
        mDoodle.shape = DoodleShape.PAINTBRUSH
    }

    // doodle ???????????? ????????????????????? ????????????
    // doodle/mosaic/draw ?????? doodle????????? ??????????????? ?????? doodle???????????? ???????????? ?????? ??? ????????? ??????.
    private var isDoodleInitialized = false

    /**
     * doodle ????????? ??????
     */
    private fun initDoodle() {
        if (isDoodleInitialized) {
            // doodle??? ?????? ????????? ?????????????????? ?????? ??????
            mDoodleView.setNewBitmap(currentBitmap!!)
            onDoodleReady()
            return
        }

        isDoodleInitialized = true
        mDoodleParams = DoodleParams()

        // doodle??? ?????????????????? id??? ????????? ?????? HashMap?????? ?????????.
        // ??????????????? ????????? ??????????????? ???????????? ????????? ??????
        for (i in 1 until color_ary.size + 1) {
            val color = color_ary[i - 1]
            try {
                val id = resources.getIdentifier("btn_doodle_color_$i", "id", packageName)
                mBtnColorIds.put(Color.parseColor(color), id)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)!!
        editor = sharedPreferences.edit()

        // mosaic??? ???????????? ?????????
        more_mosaic_thickness_seekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                setDoodleSize(progress)
                editor.putInt(DOODLE_MOSAIC_SIZE, progress)
                editor.apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val bitmap = default_image_view.drawable.toBitmap()
         mDoodle = DoodleViewWrapper(this, bitmap, mDoodleParams.mOptimizeDrawing, object : IDoodleListener {
            override fun onSaved(doodle: IDoodle, bitmap: Bitmap, callback: Runnable) { // Save the picture in jpg format
            }

            fun onError(i: Int, msg: String?) {
                setResult(RESULT_ERROR)
                finish()
            }

            override fun onReady(doodle: IDoodle) {
                onDoodleReady()
                if (mDoodleParams.mZoomerScale <= 0) {
                    menu_edit!!.findItem(R.id.menu_doodle_magnifier).isEnabled = false
                }
                mDoodle.zoomerScale = mDoodleParams.mZoomerScale
                mTouchGestureListener.isSupportScaleItem = mDoodleParams.mSupportScaleItem
            }
        }).also({ mDoodleView = it })

        mTouchGestureListener = object : DoodleOnTouchGestureListener(mDoodleView, object : ISelectionListener {
            // save states before being selected
            var mLastPen: IDoodlePen? = null
            var mLastColor: IDoodleColor? = null
            var mSize: Float? = null
            var mIDoodleItemListener = IDoodleItemListener { property ->
                if (mTouchGestureListener.selectedItem == null) {
                    return@IDoodleItemListener
                }
                if (property == IDoodleItemListener.PROPERTY_SCALE) {
                    item_scale.text = ((mTouchGestureListener.selectedItem.scale * 100 + 0.5f).toInt()).toString() + "%"
                }
            }

            override fun onSelectedItem(doodle: IDoodle, selectableItem: IDoodleSelectableItem, selected: Boolean) {
                if (selected) {
                    if (mLastPen == null) {
                        mLastPen = mDoodle.getPen()
                    }
                    if (mLastColor == null) {
                        mLastColor = mDoodle.getColor()
                    }
                    if (mSize == null) {
                        mSize = mDoodle.getSize()
                    }
                    mDoodleView.isEditMode = true
                    mDoodle.pen = selectableItem.pen
                    mDoodle.color = selectableItem.color
                    mDoodle.size = selectableItem.size
                    doodle_selectable_edit_container.beVisible()
                    item_scale.text = ((selectableItem.scale * 100 + 0.5f).toInt()).toString() + "%"
                    selectableItem.addItemListener(mIDoodleItemListener)
                } else {
                    selectableItem.removeItemListener(mIDoodleItemListener)
                    if (mTouchGestureListener.selectedItem == null) { // nothing is selected. ??????????????????????????????item
                        if (mLastPen != null) {
                            mDoodle.pen = mLastPen
                            mLastPen = null
                        }
                        if (mLastColor != null) {
                            mDoodle.color = mLastColor
                            mLastColor = null
                        }
                        if (mSize != null) {
                            mDoodle.size = mSize!!
                            mSize = null
                        }
                        doodle_selectable_edit_container.beGone()
                    }
                }
            }
        }) {
            override fun setSupportScaleItem(supportScaleItem: Boolean) {
                super.setSupportScaleItem(supportScaleItem)
                if (supportScaleItem) {
                    item_scale.beVisible()
                } else {
                    item_scale.beGone()
                }
            }
        }

        val detector: IDoodleTouchDetector = DoodleTouchDetector(applicationContext, mTouchGestureListener)
        mDoodleView.defaultTouchDetector = detector

        mDoodle.setIsDrawableOutside(mDoodleParams.mIsDrawableOutside)
        doodle_view_container.removeAllViews()
        doodle_view_container.addView(mDoodleView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        mDoodle.doodleMinScale = mDoodleParams.mMinScale
        mDoodle.doodleMaxScale = mDoodleParams.mMaxScale

        initView()
    }

    /**
     * @return ????????? ???????????? ????????? true, ????????? false
     */
    private fun canChangeColor(pen: IDoodlePen): Boolean {
        return pen !== DoodlePen.MOSAIC
    }

    private fun initView() {
        doodle_selectable_edit_container.beGone()
        item_scale.setOnLongClickListener(OnLongClickListener {
            if (mTouchGestureListener.selectedItem != null) {
                mTouchGestureListener.selectedItem.scale = 1f
            }
            true
        })
    }

    override fun onResume() {
        super.onResume()
        more_draw_thickness_seekbar.setColors(config.textColor, getAdjustedPrimaryColor(), config.backgroundColor)
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        historyManager.clearHistory()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (currDoodleAction != DOODLE_ACTION_NONE) {
            bottom_bar_close.performClick()
        } else {
            if (historyManager.canUndo() ||
                    (currPrimaryAction == PRIMARY_ACTION_CROP && gesture_crop_image_view.shouldCrop()) ||
                    isFilterApplied || isAdjustApplied) SaveBeforeExitDialog(this) {
                saveImage()
            }
            else finish()
        }
    }

    private fun initEditActivity() {
        if (intent.data == null) {
            toast(R.string.invalid_image_path)
            finish()
            return
        }

        uri = intent.data!!
        if (uri!!.scheme != "file" && uri!!.scheme != "content") {
            toast(R.string.unknown_file_location)
            finish()
            return
        }

        if (intent.extras?.containsKey(REAL_FILE_PATH) == true) {
            realFilePath = intent.extras!!.getString(REAL_FILE_PATH)!!
            uri = when {
                isPathOnOTG(realFilePath) -> uri
                realFilePath.startsWith("file:/") -> Uri.parse(realFilePath)
                else -> Uri.fromFile(File(realFilePath))
            }
        } else {
            (getRealPathFromURI(uri!!))?.apply {
                realFilePath = this
                uri = Uri.fromFile(File(this))
            }
        }

        saveUri = when {
            intent.extras?.containsKey(MediaStore.EXTRA_OUTPUT) == true -> intent.extras!!.get(MediaStore.EXTRA_OUTPUT) as Uri
            else -> uri!!
        }

        isCropIntent = intent.extras?.get(CROP) == "true"
        if (isCropIntent) {
            bottom_editor_primary_actions.beGone()
            (bottom_editor_crop_actions.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 1)
        }

        btn_compare.setOnTouchListener(this)

        preBottomAccordian = bottom_editor_crop_actions

        // ??????????????? ImageGLSurfaceView??? ???????????? ??????
        adjust_view = ImageGLSurfaceView(this, null)
        adjust_view_container.addView(adjust_view, 0)

        // ???????????? ??????????????? ??????
        bottomCropClicked()
        setupBottomActions()

        if (bottom_actions_adjust_rv.adapter == null) {
            generateAdjustRVAdapter()
        }
        if (crop_rv.adapter == null ) {
            crop_rv.layoutManager = LinearLayoutManager(applicationContext, LinearLayoutManager.HORIZONTAL, false)
            generateCropRVAdapter()
        }

        // set ucrop
        gesture_crop_image_view.setTransformImageListener(mImageListener)
        // ???????????? ????????? scroll listener ??????
        straight_ruler.setScrollingListener(object : HorizontalProgressWheelView.ScrollingListener{
            override fun onScroll(delta: Float, totalDistance: Float) {
                gesture_crop_image_view.postRotate(delta / ROTATE_WIDGET_SENSITIVITY_COEFFICIENT)
                gesture_crop_image_view.setImageToWrapOriginBounds()

                updateAspectRatio()

                if (gesture_crop_image_view.currentAngle == 0f) rotate_reset.beGone()
                else rotate_reset.beVisible()
            }

            override fun onScrollEnd() {
                crop_view_overlay.setShowCropText(false);
            }

            override fun onScrollStart() {
                gesture_crop_image_view.cancelAllAnimations()

                crop_view_overlay.setShowCropText(true);
            }

        })
    }

    private val mImageListener: TransformImageListener = object : TransformImageListener {
        override fun onRotate(currentAngle: Float) {
        }

        override fun onScaleBegin() {
            crop_view_overlay.setShowCropText(true)
        }

        override fun onScaleEnd() {
            crop_view_overlay.setShowCropText(false)
        }

        override fun onScale(currentScale: Float) {
            crop_view_overlay.setCurrentImageScale(currentScale);
        }

        override fun onLoadComplete(result: Bitmap) {

        }

        override fun onLoadFailure(e: java.lang.Exception) {
            finish()
        }
    }

    /**
     * Compare????????? ????????? ??????
     */
    override fun onTouch(view: View?, evt: MotionEvent?): Boolean {
        when (evt?.action) {
            MotionEvent.ACTION_DOWN -> {
                // ???????????? ?????????
                if (currPrimaryAction == PRIMARY_ACTION_FILTER || currPrimaryAction == PRIMARY_ACTION_MORE) {
                    default_image_view_container.beGone()
                    compare_image_view.beVisible()
                } else if (currPrimaryAction == PRIMARY_ACTION_ADJUST) {
                    adjust_view.beGone()
                    compare_image_view.beVisible()
                }
            }
            MotionEvent.ACTION_UP -> {
                // ???????????? ?????????
                if (currPrimaryAction == PRIMARY_ACTION_FILTER || currPrimaryAction == PRIMARY_ACTION_MORE) {
                    default_image_view_container.beVisible()
                    compare_image_view.beGone()
                } else if (currPrimaryAction == PRIMARY_ACTION_ADJUST) {
                    adjust_view.beVisible()
                    compare_image_view.beGone()
                }
            }
        }
        return false;
    }

    private var menu_edit: Menu ?= null
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_edit, menu)

        if (menu != null) {
            menu_edit = menu
        };

        return super.onCreateOptionsMenu(menu)
    }

    /**
     * ???????????? ????????????
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Reset ??????
            R.id.menu_reset -> {
                // ??????????????? history ???????????? ???????????????
                val originBmp = historyManager.getNativeImage()
                // ?????? history ????????? ?????????
                historyManager.resetHistory()
                updateUndoRedoButton()

                currentBitmap = createBitmap(originBmp)
                compare_image_view.setImageBitmap(originBmp)

                // ????????? ?????? history ?????????
                appliedHistory.clear()
                appliedRedoHistory.clear()

                isFilterApplied = false
                isAdjustApplied = false

                when (currPrimaryAction) {
                    PRIMARY_ACTION_CROP -> {
                        // ????????????????????? Reset???????????? ????????????
                        crop_view_overlay.setOriginCropRect(null)
                        gesture_crop_image_view.setImageBitmap(currentBitmap)
                        gesture_crop_image_view.requestLayout()
                        // ????????? ???????????? ??????
                        (crop_rv.adapter as CropSelectorRVAdapter).selectItem(0, false)

                        // ????????? ?????????????????? ????????? Reset????????? ?????? ?????? ?????? ?????????
                        rotate_reset.performClick()
                    }
                    PRIMARY_ACTION_FILTER -> {
                        // Filter???????????? Reset????????????
                        default_image_view.setImageBitmap(currentBitmap)
                        updateFilterThumb()
                    }
                    PRIMARY_ACTION_ADJUST -> {
                        // Adjust???????????? Reset????????????
                        resetAdjustSavedValues()
                        setAdjustImage(currentBitmap!!)
                    }
                    PRIMARY_ACTION_MORE -> {
                        // More???????????? Reset????????????
                        default_image_view.setImageBitmap(currentBitmap)
                    }
                }
            }

            // Save ??????
            R.id.menu_save -> {
                saveImage()
            }

            // Undo ??????(??????)
            R.id.menu_undo -> {
                // undo??? ?????????????????? ????????? ??????[0]??? ????????????[1]??? ?????????
                val list = historyManager.undo() ?: return true

                currentBitmap = list[0]
                val originBmp = list[1]
                compare_image_view.setImageBitmap(originBmp)

                // ?????? history
                if (appliedHistory.size > 0) {
                    val history = appliedHistory[appliedHistory.size - 1]
                    appliedHistory.removeAt(appliedHistory.size - 1)
                    appliedRedoHistory.add(history)
                }

                when (currPrimaryAction) {
                    PRIMARY_ACTION_CROP -> {
                        // ?????????????????????
                        crop_view_overlay.setOriginCropRect(null)
                        gesture_crop_image_view.setImageBitmap(currentBitmap)
                        gesture_crop_image_view.requestLayout()

                        // ?????? ?????? ?????????
                        val angleToZero = -gesture_crop_image_view.currentAngle
                        gesture_crop_image_view.postRotate(angleToZero)
                    }
                    PRIMARY_ACTION_FILTER -> {
                        default_image_view.setImageBitmap(currentBitmap)
                        updateFilterThumb()
                    }
                    PRIMARY_ACTION_ADJUST -> {
                        resetAdjustSavedValues()
                        setAdjustImage(currentBitmap!!)
                    }
                    PRIMARY_ACTION_MORE -> default_image_view.setImageBitmap(currentBitmap)
                }

                updateUndoRedoButton()
            }

            // Redo ??????(??????)
            R.id.menu_redo -> {
                // undo??? ?????????????????? ????????? ??????[0]??? ????????????[1]??? ?????????
                val list = historyManager.redo() ?: return true

                currentBitmap = list[0]
                val originBmp = list[1]
                compare_image_view.setImageBitmap(originBmp)

                // ?????? history
                if (appliedRedoHistory.size > 0) {
                    val history = appliedRedoHistory[appliedRedoHistory.size - 1]
                    appliedRedoHistory.removeAt(appliedRedoHistory.size - 1)
                    appliedHistory.add(history)
                }

                when (currPrimaryAction) {
                    PRIMARY_ACTION_CROP -> {
                        // ?????????????????????
                        crop_view_overlay.setOriginCropRect(null)
                        gesture_crop_image_view.setImageBitmap(currentBitmap)
                        gesture_crop_image_view.requestLayout()

                        // ?????? ?????? ?????????
                        val angleToZero = -gesture_crop_image_view.currentAngle
                        gesture_crop_image_view.postRotate(angleToZero)
                    }
                    PRIMARY_ACTION_FILTER -> {
                        default_image_view.setImageBitmap(currentBitmap)
                        updateFilterThumb()
                    }
                    PRIMARY_ACTION_ADJUST -> {
                        resetAdjustSavedValues()
                        setAdjustImage(currentBitmap!!)
                    }
                    PRIMARY_ACTION_MORE -> default_image_view.setImageBitmap(currentBitmap)
                }

                updateUndoRedoButton()
            }

            // doodle/mosaic/draw ????????? Undo ??????(??????)
            R.id.menu_doodle_undo -> {
                mDoodle.undo()
                updateDoodleUndoRedo()
            }

            // doodle/mosaic/draw ????????? Redo ??????(??????)
            R.id.menu_doodle_redo -> {
                mDoodle.redo()
                updateDoodleUndoRedo()
            }

            // doodle/mosaic/draw ????????? Edit ??????(??????)
            R.id.menu_doodle_edit -> {
                mDoodleView.isEditMode = !mDoodleView.isEditMode
            }

            // doodle/mosaic/draw ????????? Magnifier ??????(??????)
            R.id.menu_doodle_magnifier->{
                mDoodleView.enableZoomer(!mDoodleView.isEnableZoomer)

                val magnifierIcon = ContextCompat.getDrawable(this, R.drawable.baseline_search_24)!!
                if (mDoodleView.isEnableZoomer) {
                    magnifierIcon.colorFilter = PorterDuffColorFilter(getColor(R.color.text_selected_color), PorterDuff.Mode.SRC_IN)
                    menu_edit!!.findItem(R.id.menu_doodle_magnifier).icon = magnifierIcon
                } else menu_edit!!.findItem(R.id.menu_doodle_magnifier).icon = magnifierIcon
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * ????????? ????????????
     */
    private fun startCrop() {
        // ????????? ????????? ?????? True ??????
        mCropInProgress = true;

        if (scaleRateWithMaster > 1f) {
            // ???????????? downsample?????? ???????????? ?????????????????? ??????????????? ????????? ????????? ??????
            val history = AppliedValueHistory()
            history.historyType = ACTION_CROP
            history.cropAngle = gesture_crop_image_view.currentAngle
            history.cropScaleX = gesture_crop_image_view.currentScaleX / gesture_crop_image_view.initialScale
            history.cropScaleY = gesture_crop_image_view.currentScaleY / gesture_crop_image_view.initialScale
            history.cropRectInWrapper = Rect(gesture_crop_image_view.cropRectInWrapper)
            appliedHistory.add(history)
            appliedRedoHistory.clear()
        }

        // progress spinner ??????
        progress_spinner.smoothToShow()
        // currentBitmap?????? ????????? ??????
        gesture_crop_image_view.getCroppedImageAsync(currentBitmap)
    }

    /**
     *  Filter ?????? ??????
     */
    private fun loadFilterImageView() {
        if (prePrimaryAction == PRIMARY_ACTION_CROP) {
            // Crop?????? ????????? ??? ???????????? ???????????? ????????? ??????
            if (gesture_crop_image_view.shouldCrop()) {
                cropForAction = CROP_FOR_FILTER
                startCrop()
            } else {
                //e.g. ???????????? releaseCropView??? ???????????? currentBitmap??? ??????????????? ?????? ??????
                currentBitmap = createBitmap(currentBitmap!!)
                default_image_view.setImageBitmap(currentBitmap)
                updateFilterThumb()
                updateUI()
                releaseCropView()
            }
        } else{
            val preBitmap = currentBitmap
            if (prePrimaryAction == PRIMARY_ACTION_ADJUST) {
                // Adjust?????? ????????? ??????
                adjust_view.getResultBitmap {
                    currentBitmap = it
                    updateFilterThumb()
                    if (preBitmap != currentBitmap) preBitmap!!.recycle()
                    runOnUiThread{
                        default_image_view.setImageBitmap(currentBitmap)
                        updateUI()
                        if (isAdjustApplied) {
                            if (scaleRateWithMaster > 1f) {
                                // ???????????? downsample?????? ???????????? ?????????????????? ??????????????? ????????? ????????? ??????
                                var adjustStr = ""
                                for (adjustItem in appliedAdjusts) adjustStr += adjustItem.mConfigStr
                                val history = AppliedValueHistory()
                                history.historyType = ACTION_ADJUST
                                history.adjustConfigStr = adjustStr
                                appliedHistory.add(history)
                                appliedRedoHistory.clear()
                            }

                            makeHistory(currentBitmap!!)
                        }
                    }
                }
                // Adjust??? glSurfaceView ?????? ??????
                recreateAdjustView()
            } else if (prePrimaryAction == PRIMARY_ACTION_MORE) {
                // More?????? ????????? ?????? ?????? ImageView??? ??????????????? ?????? ????????? ???????????? ?????? ??????
                currentBitmap = default_image_view.drawable.toBitmap()
                updateFilterThumb()
                if (preBitmap != currentBitmap) preBitmap!!.recycle()
                updateUI()
            }
        }
    }

    /**
     * Filter ImageView??? ????????? ???????????? recycle??????
     */
    private fun releaseFilterView() {
        val imageDrawable: Drawable = default_image_view.drawable
        default_image_view.setImageDrawable(null)
        if (imageDrawable != null && imageDrawable is BitmapDrawable) {
            val bitmapDrawable: BitmapDrawable = imageDrawable as BitmapDrawable
            if (!bitmapDrawable.bitmap.isRecycled) bitmapDrawable.bitmap.recycle()
        }
    }

    /**
     * Crop ImageView??? ????????? ???????????? recycle??????
     */
    private fun releaseCropView() {
        val imageDrawable: Drawable = gesture_crop_image_view.drawable
        gesture_crop_image_view.setImageDrawable(null)
        if (imageDrawable != null && imageDrawable is FastBitmapDrawable) {
            val bitmapDrawable: FastBitmapDrawable = imageDrawable as FastBitmapDrawable
            if (!bitmapDrawable.bitmap.isRecycled) bitmapDrawable.bitmap.recycle()
        }
    }

    /**
     * ?????? ImageView??? ????????? ???????????? recycle??????
     */
    private fun releaseCompareView() {
        val imageDrawable: Drawable = compare_image_view.drawable
        gesture_crop_image_view.setImageDrawable(null)
        if (imageDrawable != null && imageDrawable is BitmapDrawable) {
            val bitmapDrawable: BitmapDrawable = imageDrawable as BitmapDrawable
            if (!bitmapDrawable.bitmap.isRecycled) bitmapDrawable.bitmap.recycle()
        }
    }

    // Adjust??? glSurfaceView??? ?????? ???????????? ????????????
    // ???????????? glSurfaceView??? ?????? ?????? & ???????????? ?????????????????? ?????????????????? ??? ??????
    private fun recreateAdjustView() {
        ensureBackgroundThread {
            adjust_view.onPause()
            adjust_view.release()
            adjust_view = ImageGLSurfaceView(this, null)
            adjust_view.displayMode = ImageGLSurfaceView.DisplayMode.DISPLAY_ASPECT_FIT
            runOnUiThread {
                adjust_view_container.removeViewAt(0)
                adjust_view_container.addView(adjust_view, 0)
            }
        }
    }

    /**
     * ??????????????? thumb????????? ?????? ???????????? ???????????? ???????????????????????? ????????????
     */
    private fun updateFilterThumb() {
        ensureBackgroundThread {
            val thumbSquare = getFilterThumb()
            runOnUiThread {
                val adapter = bottom_actions_filter_rv.adapter
                (adapter as FiltersAdapter).updateFilterThumb(thumbSquare)
                adapter.notifyDataSetChanged()
            }
        }
    }

    /**
     * ??????????????? thumb?????? ??????.
     */
    private fun getFilterThumb(): Bitmap {

        val thumbnailSize = resources.getDimension(R.dimen.bottom_filters_thumbnail_size).toInt();

        var newWidth = thumbnailSize
        var newHeight = thumbnailSize
        if (currentBitmap!!.width <= currentBitmap!!.height) newHeight = (thumbnailSize.toFloat() / currentBitmap!!.width * currentBitmap!!.height).toInt()
        else newWidth = (thumbnailSize.toFloat() / currentBitmap!!.height * currentBitmap!!.width).toInt()

        val thumbBmp = Bitmap.createScaledBitmap(currentBitmap!!, newWidth, newHeight, true);
        val thumbWidth = thumbBmp.width
        val thumbHeight = thumbBmp.height
        val thumbSquare: Bitmap?
        if (thumbHeight > thumbWidth) thumbSquare = Bitmap.createBitmap(thumbBmp, 0, (thumbHeight - thumbWidth) / 2, thumbWidth, thumbWidth);
        else thumbSquare = Bitmap.createBitmap(thumbBmp, (thumbWidth - thumbHeight) / 2, 0, thumbHeight, thumbHeight);

        if (thumbBmp != thumbSquare) thumbBmp.recycle()

        return thumbSquare
    }

    // ????????? AdjustConfig ??????
    var appliedAdjusts = ArrayList<AdjustConfig>()
    /**
     *  Adjust ?????? ??????
     */
    private fun loadAdjustImageView() {
        isAdjustApplied = false
        if (prePrimaryAction == PRIMARY_ACTION_CROP) {
            // Crop?????? ????????? ?????? ???????????? ???????????? ????????? ??????
            if (gesture_crop_image_view.shouldCrop()) {
                cropForAction = CROP_FOR_ADJUST
                startCrop()
            } else {
                //e.g. ???????????? releaseCropView??? ???????????? currentBitmap??? ??????????????? ?????? ??????
                currentBitmap = createBitmap(currentBitmap!!)
                releaseCropView()
                setAdjustImage(currentBitmap!!)
                updateUI()
            }
        } else {
            val preBitmap = currentBitmap
            if (prePrimaryAction == PRIMARY_ACTION_FILTER) {
                // Filter?????? ????????? ??????
                currentBitmap = createBitmap(default_image_view.drawable.toBitmap())
                releaseFilterView()
                setAdjustImage(currentBitmap!!)
                if (isFilterApplied) {
                    if (scaleRateWithMaster > 1f) {
                        // ???????????? downsample?????? ???????????? ?????????????????? ??????????????? ????????? ????????? ??????
                        val history = AppliedValueHistory()
                        history.historyType = ACTION_FILTER
                        history.filterConfigStr = (bottom_actions_filter_rv.adapter as FiltersAdapter).getCurrentFilter().filter.second
                        appliedHistory.add(history)
                        appliedRedoHistory.clear()
                    }

                    makeHistory(currentBitmap!!)
                }
                if (preBitmap != currentBitmap) preBitmap!!.recycle()

            } else if (prePrimaryAction == PRIMARY_ACTION_MORE) {
                // More?????? ????????? ??????
                currentBitmap = createBitmap(default_image_view.drawable.toBitmap())
                releaseFilterView()
                setAdjustImage(currentBitmap!!)
                if (preBitmap != currentBitmap) preBitmap!!.recycle()
            }
            updateUI()
        }

        // Adjust??? Curve????????? ?????? canvas ?????????
        if (!rgb_curve_canvas.isInitialized) {
            rgb_curve_canvas.initCanvasSpliner(dpToPx(350) - 20, dpToPx(400) - 20)

            val channels = arrayOf(CURVE_CHANNEL_RGB, CURVE_CHANNEL_R, CURVE_CHANNEL_G, CURVE_CHANNEL_B)

            rgb_channel_spinner.setItems(channels.toList())
            rgb_channel_spinner.setOnSpinnerItemSelectedListener(object : OnSpinnerItemSelectedListener<String?> {
                override fun onItemSelected(oldIndex: Int, @Nullable oldItem: String?, newIndex: Int, newItem: String?) {
                    // ????????? channel??? ?????? ??????????????? ????????? canvas??? ?????????
                    currentRGBChannel = channels[newIndex]

                    when (currentRGBChannel) {
                        CURVE_CHANNEL_RGB -> if (curve_points_rgb.isNotEmpty()) rgb_curve_canvas.replacePoints(curve_points_rgb)
                        else rgb_curve_canvas.resetPoints()
                        CURVE_CHANNEL_R -> if (curve_points_r.isNotEmpty()) rgb_curve_canvas.replacePoints(curve_points_r)
                        else rgb_curve_canvas.resetPoints()
                        CURVE_CHANNEL_G -> if (curve_points_g.isNotEmpty())  rgb_curve_canvas.replacePoints(curve_points_g)
                        else rgb_curve_canvas.resetPoints()
                        CURVE_CHANNEL_B -> if (curve_points_b.isNotEmpty())  rgb_curve_canvas.replacePoints(curve_points_b)
                        else rgb_curve_canvas.resetPoints()
                    }
                }
            })
        }

        // Adjust ???????????? ?????????
        appliedAdjusts.clear()
        // ?????? ?????????
        adjust_view.setFilterWithConfig("")
        // ???????????? ????????? ??????, ????????? ?????? ??????
        (bottom_actions_adjust_rv.adapter as AdjustAdapter).resetItems()
        (bottom_actions_adjust_rv.adapter as AdjustAdapter).selectItem(0)

        // curve????????? ?????????
        curve_points_rgb.clear()
        curve_points_r.clear()
        curve_points_g.clear()
        curve_points_b.clear()
        // curve??? ????????? channel ??????
        rgb_channel_spinner.selectItemByIndex(0)
        rgb_curve_canvas.resetPoints()
    }
    fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    /**
     * ??????????????? ????????? bitmap??? Adjust??? ???????????? 0?????? item ??????
     */
    private fun setAdjustImage(bitmap: Bitmap) {
        adjust_view.setImageBitmap(bitmap)
        (bottom_actions_adjust_rv.adapter as AdjustAdapter).selectItem(0)
    }

    /**
     * More ?????? ??????
     */
    private fun loadMoreImageView() {
        if (prePrimaryAction == PRIMARY_ACTION_CROP) {
            // Crop?????? ????????? ?????? ???????????? ???????????? ????????? ??????
            if (gesture_crop_image_view.shouldCrop()) {
                cropForAction = CROP_FOR_MORE
                startCrop()
            } else {
                //e.g. ???????????? releaseCropView??? ???????????? currentBitmap??? ??????????????? ?????? ??????
                currentBitmap = createBitmap(currentBitmap!!)
                default_image_view.setImageBitmap(currentBitmap)
                updateUI()
                releaseCropView()
            }
        } else {
            val preBitmap = currentBitmap
            if (prePrimaryAction == PRIMARY_ACTION_ADJUST) {
                // Adjust?????? ????????? ??????
                adjust_view.getResultBitmap {
                    currentBitmap = it
                    if (preBitmap != currentBitmap) preBitmap!!.recycle()
                    runOnUiThread {
                        default_image_view.setImageBitmap(currentBitmap)
                        updateUI()
                        if (isAdjustApplied) {
                            if (scaleRateWithMaster > 1f) {
                                // ???????????? downsample?????? ???????????? ?????????????????? ??????????????? ????????? ????????? ??????
                                var adjustStr = ""
                                for (adjustItem in appliedAdjusts) adjustStr += adjustItem.mConfigStr
                                val history = AppliedValueHistory()
                                history.historyType = ACTION_ADJUST
                                history.adjustConfigStr = adjustStr
                                appliedHistory.add(history)
                                appliedRedoHistory.clear()
                            }
                            makeHistory(currentBitmap!!)
                        }
                    }
                }
                // Adjust??? glSurfaceView ?????? ??????
                recreateAdjustView()
            } else if (prePrimaryAction == PRIMARY_ACTION_FILTER) {
                // Filter?????? ????????? ??????
                // Filter??? More??? ?????? ImageView??? ??????????????? ?????? ????????? ????????? ??????
                currentBitmap = default_image_view.drawable.toBitmap()
                if (isFilterApplied) {
                    if (scaleRateWithMaster > 1f) {
                        // ???????????? downsample?????? ???????????? ?????????????????? ??????????????? ????????? ????????? ??????
                        val history = AppliedValueHistory()
                        history.historyType = ACTION_FILTER
                        history.filterConfigStr = (bottom_actions_filter_rv.adapter as FiltersAdapter).getCurrentFilter().filter.second
                        appliedHistory.add(history)
                        appliedRedoHistory.clear()
                    }

                    makeHistory(currentBitmap!!)
                }
                if (preBitmap != currentBitmap) preBitmap!!.recycle()
                updateUI()
            }
        }

        val thisActivity = this
        btn_doodle.setOnClickListener(object : View.OnClickListener{
            override fun onClick(p0: View?) {
                // More?????? doodle ??????????????????
                if (mCropInProgress) return

                currDoodleAction = DOODLE_ACTION_DOODLE
                // show doodle window
                initDoodle()

                doodle_image_view.beVisible()
                default_image_view_container.beGone()

                bottom_editor_more_actions.beGone()
                bottom_editor_primary_actions.beGone()
                btn_compare.beGone()

                bottom_editor_doodle_actions.beVisible()
                bottom_close_check_bar.beVisible()
                bottom_bar_check_close_name.text = getString(R.string.doodle)

                draw_spinner_container.beGone()

                controlBarAnim(bottom_bar_container);
                mDoodle.setBrushBMPStyle(1)                   // normal brush

                // Edit?????? ?????? ????????? doodle?????? ?????????
                menu_edit!!.setGroupVisible(R.id.edit_menu_group, false)
                menu_edit!!.setGroupVisible(R.id.doodle_menu_group, true)
                bottom_bar_close.setOnClickListener {
                    // ???????????????(??????)????????? ???????????? ??????????????? ????????? ????????? ??????
                    if (mDoodleView.itemCount > 0) {
                        SaveChangesDialog(thisActivity, {
                            // ??????
                            doodleCancel()
                        }, {
                            // ??????
                            bottom_bar_check.performClick()
                        })
                    } else doodleCancel()
                }

                bottom_bar_check.setOnClickListener {
                    // ????????????(??????)????????? ????????????
                    currDoodleAction = DOODLE_ACTION_NONE
                    bottom_editor_doodle_actions.beGone()
                    bottom_close_check_bar.beGone()

                    bottom_editor_more_actions.beVisible()
                    bottom_editor_primary_actions.beVisible()
                    btn_compare.beVisible()

                    if (mDoodleView.isEnableZoomer) menu_edit!!.performIdentifierAction (R.id.menu_doodle_magnifier, 0)

                    controlBarAnim(bottom_bar_container);

                    // ??????????????? ????????? ???????????? ??????
                    if (mDoodleView.itemCount > 0) {
                        // ???????????? item?????? ?????????
                        mDoodleView.drawPendingItems()

                        // ??????????????? ???????????? ???????????? ?????????
                        if (mDoodleView.isEditMode) menu_edit!!.performIdentifierAction (R.id.menu_doodle_edit, 0)

                        val temp = currentBitmap
                        currentBitmap = createBitmap(mDoodleView.doodleBitmap)
                        if (temp != currentBitmap) temp!!.recycle()
                        mDoodleView.doodleBitmap.recycle()
                        default_image_view.setImageBitmap(currentBitmap)
                        // history ??????
                        makeHistory(currentBitmap!!)

                        if (scaleRateWithMaster > 1f) {
                            // ???????????? downsample?????? ???????????? ?????????????????? ??????????????? ????????? ????????? ??????
                            val history = AppliedValueHistory()
                            history.historyType = ACTION_DOODLE
                            history.doodleItems = ArrayList(mDoodle.allItem)
                            appliedHistory.add(history)
                            appliedRedoHistory.clear()
                        }
                    }
                    doodle_image_view.beGone()
                    default_image_view_container.beVisible()

                    // doodle?????? ????????? Edit?????? ?????? ?????????
                    menu_edit!!.setGroupVisible(R.id.edit_menu_group, true)
                    menu_edit!!.setGroupVisible(R.id.doodle_menu_group, false)
                }
            }
        });

        btn_mosaic.setOnClickListener(object : View.OnClickListener{
            override fun onClick(p0: View?) {
                // More?????? mosaic ??????????????????
                if (mCropInProgress) return
                currDoodleAction = DOODLE_ACTION_MOSAIC
                // show doodle window
                initDoodle()

                doodle_image_view.beVisible()
                default_image_view_container.beGone()

                bottom_editor_more_actions.beGone()
                bottom_editor_primary_actions.beGone()
                btn_compare.beGone()

                more_mosaic_thickness_seekbar.setColors(config.textColor, getAdjustedPrimaryColor(), config.backgroundColor)
                bottom_editor_mosaic_actions.beVisible()
                bottom_close_check_bar.beVisible()
                bottom_bar_check_close_name.text = getString(R.string.doodle_bar_mosaic)
                draw_spinner_container.beGone()

                controlBarAnim(bottom_bar_container);
                mDoodle.setBrushBMPStyle(1)                          // normal brush

                // Edit?????? ?????? ????????? doodle?????? ?????????
                menu_edit!!.setGroupVisible(R.id.edit_menu_group, false)
                menu_edit!!.setGroupVisible(R.id.doodle_menu_group, true)
                bottom_bar_close.setOnClickListener {
                    // ???????????????(??????)????????? ???????????? ??????????????? ????????? ????????? ??????
                    if (mDoodleView.itemCount > 0) {
                        SaveChangesDialog(thisActivity, {
                            // ??????
                            mosaicCancel()
                        }, {
                            // ??????
                            bottom_bar_check.performClick()
                        })
                    } else mosaicCancel()
                }

                bottom_bar_check.setOnClickListener {
                    // ????????????(??????)????????? ????????????
                    currDoodleAction = DOODLE_ACTION_NONE
                    bottom_editor_mosaic_actions.beGone()
                    bottom_close_check_bar.beGone()

                    bottom_editor_more_actions.beVisible()
                    bottom_editor_primary_actions.beVisible()
                    btn_compare.beVisible()

                    if (mDoodleView.isEnableZoomer) menu_edit!!.performIdentifierAction (R.id.menu_doodle_magnifier, 0)

                    controlBarAnim(bottom_bar_container);

                    // ??????????????? ????????? ???????????? ??????
                    if (mDoodleView.itemCount > 0) {
                        // ???????????? item?????? ?????????
                        mDoodleView.drawPendingItems()
                        // ??????????????? ???????????? ???????????? ?????????
                        if (mDoodleView.isEditMode) menu_edit!!.performIdentifierAction (R.id.menu_doodle_edit, 0)

                        val temp = currentBitmap
                        currentBitmap = createBitmap(mDoodleView.doodleBitmap)
                        if (temp != currentBitmap) temp!!.recycle()
                        mDoodleView.doodleBitmap.recycle()
                        default_image_view.setImageBitmap(currentBitmap)
                        // history ??????
                        makeHistory(currentBitmap!!)

                        if (scaleRateWithMaster > 1f) {
                            // ???????????? downsample?????? ???????????? ?????????????????? ??????????????? ????????? ????????? ??????
                            val history = AppliedValueHistory()
                            history.historyType = ACTION_MOSAIC
                            history.mosaicItems = ArrayList(mDoodle.allItem)
                            appliedHistory.add(history)
                            appliedRedoHistory.clear()
                        }
                    }

                    doodle_image_view.beGone()
                    default_image_view_container.beVisible()
                    // doodle?????? ????????? Edit?????? ?????? ?????????
                    menu_edit!!.setGroupVisible(R.id.edit_menu_group, true)
                    menu_edit!!.setGroupVisible(R.id.doodle_menu_group, false)
                }
            }
        })

        btn_draw.setOnClickListener(object : View.OnClickListener{
            override fun onClick(view: View?) {
                // draw
                if (mCropInProgress) return
                currDoodleAction = DOODLE_ACTION_DRAW
                // show doodle window
                initDoodle()

                doodle_image_view.beVisible()

                default_image_view_container.beGone()

                bottom_editor_more_actions.beGone()
                bottom_editor_primary_actions.beGone()
                btn_compare.beGone()

                bottom_close_check_bar.beVisible()
                bottom_bar_check_close_name.beGone()
                draw_spinner_container.beVisible()

                controlBarAnim(bottom_bar_container);
                // Edit?????? ?????? ????????? doodle?????? ?????????
                menu_edit!!.setGroupVisible(R.id.edit_menu_group, false)
                menu_edit!!.setGroupVisible(R.id.doodle_menu_group, true)
                // set draw-menu select listener
                val menuArray = resources.getStringArray(R.array.more_draw_menu)
                val staticAdapter = ArrayAdapter.createFromResource(view!!.context, R.array.more_draw_menu,
                        android.R.layout.simple_spinner_item)
                staticAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                bottom_bar_menu?.adapter = staticAdapter;

                var last_spinner_pos = 0
                // call setSelection, because need one selection at first.
                // if not call, automatically call 0
                bottom_bar_menu.setSelection(0)
                bottom_bar_menu?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {}

                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position < 3) {
                            last_spinner_pos = position
                            arrayOf(bottom_more_draw_style, bottom_more_draw_size, bottom_more_draw_color).forEach {
                                it?.beGone()
                            }
                        }
                        when (position) {
                            0 -> {
                                // style selected
                                bottom_more_draw_style.beVisible()
                                // set saved style
                                currMoreDrawStyle = config.lastEditorBrushStyle
                                highlightDrawStyleButtonBorder()
                                spinner_text.text = menuArray[0]
                            }
                            1 -> {
                                // size selected
                                bottom_more_draw_size.beVisible()
                                more_draw_thickness_seekbar.setColors(config.textColor, getAdjustedPrimaryColor(), config.backgroundColor)
                                more_draw_thickness_seekbar.progress = config.lastEditorBrushSize
                                spinner_text.text = menuArray[1]
                            }
                            2 -> {
                                // color selected
                                bottom_more_draw_color.beVisible()

                                // select saved color
                                when (config.lastEditorDrawColorNum) {
                                    0 -> {
                                        currMoreDrawColor = MORE_DRAW_COLOR_0
                                    }
                                    1 -> {
                                        currMoreDrawColor = MORE_DRAW_COLOR_1
                                    }
                                    2 -> {
                                        currMoreDrawColor = MORE_DRAW_COLOR_2
                                    }
                                    3 -> {
                                        currMoreDrawColor = MORE_DRAW_COLOR_3
                                    }
                                    4 -> {
                                        currMoreDrawColor = MORE_DRAW_COLOR_4
                                    }
                                }
                                highlightDrawColorButtonBorder()
                                spinner_text.text = menuArray[2]
                            }
                            3 -> {
                                // clear
                                mDoodle.clear()
                                bottom_bar_menu.setSelection(last_spinner_pos)
                            }
                        }
                    }
                }

                // set saved brush color/size
                when (config.lastEditorDrawColorNum) {
                    0 -> {
                        updateDrawColor(MORE_DRAW_COLOR_0)
                    }
                    1 -> {
                        updateDrawColor(MORE_DRAW_COLOR_1)
                    }
                    2 -> {
                        updateDrawColor(MORE_DRAW_COLOR_2)
                    }
                    3 -> {
                        updateDrawColor(MORE_DRAW_COLOR_3)
                    }
                    4 -> {
                        updateDrawColor(MORE_DRAW_COLOR_4)
                    }
                }

                bottom_bar_close.setOnClickListener {
                    // ???????????????(??????)????????? ???????????? ??????????????? ????????? ????????? ??????
                    if (mDoodleView.itemCount > 0) {
                        SaveChangesDialog(thisActivity, {
                            // ??????
                            drawCancel()
                        }, {
                            // ??????
                            bottom_bar_check.performClick()
                        })
                    } else drawCancel()
                }

                bottom_bar_check.setOnClickListener {
                    // ????????????(??????)????????? ????????????
                    currDoodleAction = DOODLE_ACTION_NONE
                    bottom_close_check_bar.beGone()
                    bottom_more_draw_style.beGone()
                    bottom_more_draw_size.beGone()
                    bottom_more_draw_color.beGone()

                    bottom_editor_more_actions.beVisible()
                    bottom_editor_primary_actions.beVisible()
                    btn_compare.beVisible()

                    if (mDoodleView.isEnableZoomer) menu_edit!!.performIdentifierAction (R.id.menu_doodle_magnifier, 0)

                    controlBarAnim(bottom_bar_container);

                    // ??????????????? ????????? ???????????? ??????
                    if (mDoodleView.itemCount > 0) {
                        // ???????????? item??? ?????????
                        mDoodleView.drawPendingItems()
                        // ??????????????? ???????????? ???????????? ?????????
                        if (mDoodleView.isEditMode) menu_edit!!.performIdentifierAction (R.id.menu_doodle_edit, 0)

                        val temp = currentBitmap
                        currentBitmap = createBitmap(mDoodleView.doodleBitmap)
                        if (temp != currentBitmap) temp!!.recycle()
                        mDoodleView.doodleBitmap.recycle()
                        default_image_view.setImageBitmap(currentBitmap)
                        // history ??????
                        makeHistory(currentBitmap!!)
                        if (scaleRateWithMaster > 1f) {
                            // ???????????? downsample?????? ???????????? ?????????????????? ??????????????? ????????? ????????? ??????
                            val history = AppliedValueHistory()
                            history.historyType = ACTION_DRAW
                            history.drawItems = ArrayList(mDoodle.allItem)
                            appliedHistory.add(history)
                            appliedRedoHistory.clear()
                        }
                    }

                    doodle_image_view.beGone()
                    default_image_view_container.beVisible()
                    // doodle?????? ????????? Edit?????? ?????? ?????????
                    menu_edit!!.setGroupVisible(R.id.edit_menu_group, true)
                    menu_edit!!.setGroupVisible(R.id.doodle_menu_group, false)
                }
            }
        })
    }

    /**
     * doodle??? ????????????????????????
     */
    private fun doodleCancel() {
        currDoodleAction = DOODLE_ACTION_NONE
        bottom_editor_doodle_actions.beGone()
        bottom_close_check_bar.beGone()

        bottom_editor_more_actions.beVisible()
        bottom_editor_primary_actions.beVisible()
        btn_compare.beVisible()

        doodle_image_view.beGone()
        default_image_view_container.beVisible()

        if (mDoodleView.isEditMode) menu_edit!!.performIdentifierAction(R.id.menu_doodle_edit, 0)
        if (mDoodleView.isEnableZoomer) menu_edit!!.performIdentifierAction(R.id.menu_doodle_magnifier, 0)

        controlBarAnim(bottom_bar_container);
        menu_edit!!.setGroupVisible(R.id.edit_menu_group, true)
        menu_edit!!.setGroupVisible(R.id.doodle_menu_group, false)
    }

    /**
     * mosaic??? ????????????????????????
     */
    private fun mosaicCancel() {
        currDoodleAction = DOODLE_ACTION_NONE
        bottom_editor_mosaic_actions.beGone()
        bottom_close_check_bar.beGone()

        bottom_editor_more_actions.beVisible()
        bottom_editor_primary_actions.beVisible()
        btn_compare.beVisible()

        doodle_image_view.beGone()
        default_image_view_container.beVisible()

        if (mDoodleView.isEditMode) menu_edit!!.performIdentifierAction (R.id.menu_doodle_edit, 0)
        if (mDoodleView.isEnableZoomer) menu_edit!!.performIdentifierAction (R.id.menu_doodle_magnifier, 0)

        controlBarAnim(bottom_bar_container);
        menu_edit!!.setGroupVisible(R.id.edit_menu_group, true)
        menu_edit!!.setGroupVisible(R.id.doodle_menu_group, false)
    }

    /**
     * draw??? ????????????????????????
     */
    private fun drawCancel() {
        currDoodleAction = DOODLE_ACTION_NONE
        bottom_close_check_bar.beGone()
        bottom_more_draw_style.beGone()
        bottom_more_draw_size.beGone()
        bottom_more_draw_color.beGone()

        bottom_editor_more_actions.beVisible()
        bottom_editor_primary_actions.beVisible()
        btn_compare.beVisible()

        if (mDoodleView.isEditMode) menu_edit!!.performIdentifierAction (R.id.menu_doodle_edit, 0)
        if (mDoodleView.isEnableZoomer) menu_edit!!.performIdentifierAction (R.id.menu_doodle_magnifier, 0)

        controlBarAnim(bottom_bar_container);

        doodle_image_view.beGone()
        default_image_view_container.beVisible()
        menu_edit!!.setGroupVisible(R.id.edit_menu_group, true)
        menu_edit!!.setGroupVisible(R.id.doodle_menu_group, false)
    }

    /**
     * ????????? ?????? ??????
     */
    private fun loadCropImageView() {
        if (currentBitmap == null) {
            // ??????????????? ??????????????? ?????????
            progress_spinner.smoothToShow()
            loadBitmapFromUri()
            gesture_crop_image_view.setOnCropImageCompleteListener(this@EditActivity)
            gesture_crop_image_view.setKeepAspectRatio(false);
            updateUI()
        } else  {
            val preBitmap = currentBitmap
            if (prePrimaryAction == PRIMARY_ACTION_FILTER) {
                //Filter?????? ????????? ???
                currentBitmap = createBitmap(default_image_view.drawable.toBitmap())
                if (isFilterApplied) {
                    if (scaleRateWithMaster > 1f) {
                        // ???????????? downsample?????? ???????????? ?????????????????? ??????????????? ????????? ????????? ??????
                        val history = AppliedValueHistory()
                        history.historyType = ACTION_FILTER
                        history.filterConfigStr = (bottom_actions_filter_rv.adapter as FiltersAdapter).getCurrentFilter().filter.second
                        appliedHistory.add(history)
                        appliedRedoHistory.clear()
                    }

                    makeHistory(currentBitmap!!)
                }
                gesture_crop_image_view.setImageBitmap(currentBitmap)
                updateUI()
                if (preBitmap != currentBitmap) preBitmap!!.recycle()
                releaseFilterView()
            } else if (prePrimaryAction == PRIMARY_ACTION_ADJUST) {
                // Adjust?????? ????????? ???
                adjust_view.getResultBitmap {
                    currentBitmap = it
                    if (preBitmap != currentBitmap) preBitmap!!.recycle()
                    runOnUiThread {
                        gesture_crop_image_view.setImageBitmap(currentBitmap)
                        updateUI()
                        if (isAdjustApplied) {
                            if (scaleRateWithMaster > 1f) {
                                // ???????????? downsample?????? ???????????? ?????????????????? ??????????????? ????????? ????????? ??????
                                var adjustStr = ""
                                for (adjustItem in appliedAdjusts) adjustStr += adjustItem.mConfigStr
                                val history = AppliedValueHistory()
                                history.historyType = ACTION_ADJUST
                                history.adjustConfigStr = adjustStr
                                appliedHistory.add(history)
                                appliedRedoHistory.clear()
                            }

                            makeHistory(currentBitmap!!)
                        }
                    }
                }
                recreateAdjustView()
            } else if (prePrimaryAction == PRIMARY_ACTION_MORE){
                // More?????? ????????? ???
                currentBitmap = createBitmap(default_image_view.drawable.toBitmap())
                gesture_crop_image_view.setImageBitmap(currentBitmap)
                updateUI()
                if (preBitmap != currentBitmap) preBitmap!!.recycle()
                releaseFilterView()
            }
            crop_view_overlay.setOriginCropRect(null)                           // CropImageView onLayout() -> onImageLaidOut()??? ??????????????? setOriginCropRect??? ?????? ????????????.
            // ???????????? ??????
            (crop_rv.adapter as CropSelectorRVAdapter).selectItem(0, false)
        }

        // ????????????????????? ????????? ?????????????????? ???????????? Reset????????? click listener
        rotate_reset.setOnClickListener {
            val resetAngle = getResetAngle();
            // ????????? ?????????????????? ?????????
            if (gesture_crop_image_view.isPortrait) crop_view_overlay.setCurrentImageScale(gesture_crop_image_view.originScalePortrait)
            else crop_view_overlay.setCurrentImageScale(gesture_crop_image_view.originScaleLandscape)

            // ????????? ?????? ?????????
            gesture_crop_image_view.postRotate(resetAngle)
            // ????????? ??????????????? ??????????????? ??????
            gesture_crop_image_view.setImageToWrapOriginBounds()
            rotate_reset.beGone()
            straight_ruler.reset()

            // ?????????????????? ?????? ????????? ?????????
            updateAspectRatio()
        }
    }

    /**
     * Reset????????? ???????????? ??????????????? ?????? ?????????
     */
    private fun getResetAngle(): Float {
        var resetAngle = gesture_crop_image_view.currentAngle;
        val scaleX = gesture_crop_image_view.currentScaleX;
        val scaleY = gesture_crop_image_view.currentScaleY;

        if (gesture_crop_image_view.isPortrait) {
            if (scaleX > 0) {
                if (scaleY > 0) resetAngle *= -1
            } else {
                resetAngle = if (scaleY > 0) {
                    if (resetAngle > 0) resetAngle - 180f
                    else 180f + resetAngle
                } else {
                    if (resetAngle > 0) 180f - resetAngle
                    else -180f - resetAngle
                }
            }
        } else {
            if (scaleX > 0) {
                resetAngle = if (scaleY > 0) {
                    if (resetAngle > 0) 90f - resetAngle
                    else -90f - resetAngle
                } else {
                    if (resetAngle > 0) resetAngle - 90f
                    else 90f + resetAngle
                }
            } else {
                resetAngle = if (scaleY > 0) {
                    if (resetAngle > 0) resetAngle - 90f
                    else 90f + resetAngle
                } else {
                    if (resetAngle > 0) 90f - resetAngle
                    else -90f - resetAngle
                }
            }
        }
        return resetAngle;
    }

    // ?????????????????? ????????????
    // ????????? ????????? 6MP??? ????????? 1f?????? ?????? ????????? 1f
    private var scaleRateWithMaster = 1f

    /**
     * Glide??? ???????????? ?????? ????????? uri????????? ????????????
     */
    private fun loadBitmapFromUri() {
        // save original bitmap
        if (currentBitmap == null) {

            // ??????????????? ???????????? ????????? ????????? ?????????
            val fileDirItem = realFilePath.let { FileDirItem(it, realFilePath.getFilenameFromPath(), getIsPathDirectory(realFilePath)) }
            val size = fileDirItem.getResolution(this)

            // Glide ??????
            val options = RequestOptions()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)

            if (size!!.x * size.y > 6000000) {
                val squreRate = size.x * size.y / 6000000f
                scaleRateWithMaster = sqrt(squreRate.toDouble()).toFloat()
                options.override((size.x / scaleRateWithMaster).toInt(), (size.y / scaleRateWithMaster).toInt())
            } else options.downsample(DownsampleStrategy.NONE)

            ensureBackgroundThread {
                // Glide ??????
                Glide.with(this)
                    .asBitmap()
                    .apply(options)
                    .load(uri).listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>?, isFirstResource: Boolean): Boolean {
                            showErrorToast(e.toString())
                            return false
                        }

                        override fun onResourceReady(originBmp: Bitmap?, model: Any?, target: Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            currentBitmap = originBmp!!

                            runOnUiThread {
                                // ????????? ???????????? Filter???????????? RecyclerView Adapter ??????
                                // ????????? ?????? thumb??? ???????????? ????????????
                                if (bottom_actions_filter_rv.adapter == null) {
                                    generateFilterRVAdapter()
                                }

                                // ???????????? history??? ??????
                                makeHistory(currentBitmap!!)
                                // ???????????? ??????
                                compare_image_view.setImageBitmap(createBitmap(originBmp))
                                // ??????????????? ??????
                                gesture_crop_image_view.setImageBitmap(currentBitmap)
                                // Adjust??? ???????????? ??????
                                adjust_view.displayMode = ImageGLSurfaceView.DisplayMode.DISPLAY_ASPECT_FIT

                                progress_spinner.smoothToHide()
                                // ????????? ???????????? ?????? ?????? ????????? ?????? ????????? ????????? ??????View??? ?????????
                                bottom_bar_cover.beGone()
                            }

                            return false
                        }
                    }).submit().get()
            }
        }
    }

    /**
     * ????????? ???????????? ?????? ?????? ?????? ??????
     * ????????? ????????? 6MP ?????? ??? ?????????????????? ??????????????? ???????????? ??????????????? ????????? ????????????
     */
    @TargetApi(Build.VERSION_CODES.N)
    private fun saveImage() {
        // ????????? ???????????? ?????? ?????? ????????? ?????? ????????? ??????View??? ??????
        bottom_bar_cover.beVisible()
        var inputStream: InputStream? = null
        try {
            if (isNougatPlus()) {
                inputStream = contentResolver.openInputStream(uri!!)
                oldExif = ExifInterface(inputStream!!)
            }
        } catch (e: Exception) {
        } finally {
            inputStream?.close()
        }

        if (scaleRateWithMaster == 1f) {
            if (currPrimaryAction == PRIMARY_ACTION_CROP) {
                // ????????? ?????????????????? ?????? ???????????? ????????????
                cropForAction = CROP_FOR_SAVE
                gesture_crop_image_view.getCroppedImageAsync(gesture_crop_image_view.viewBitmap)
            } else if (currPrimaryAction == PRIMARY_ACTION_FILTER || currPrimaryAction == PRIMARY_ACTION_MORE) {
                val bitmap = default_image_view.drawable.toBitmap()
                saveBitmap(bitmap!!)
            } else if (currPrimaryAction == PRIMARY_ACTION_ADJUST) {
                adjust_view.getResultBitmap { saveBitmap(it) }
            }
        } else {
            // ????????? downsample ?????????
            if (currPrimaryAction == PRIMARY_ACTION_CROP) {
                // ???????????? downsample?????? ???????????? ?????????????????? ??????????????? ????????? ????????? ??????
                val history = AppliedValueHistory()
                history.historyType = ACTION_CROP
                history.cropAngle = gesture_crop_image_view.currentAngle
                history.cropScaleX = gesture_crop_image_view.currentScaleX / gesture_crop_image_view.initialScale
                history.cropScaleY = gesture_crop_image_view.currentScaleY / gesture_crop_image_view.initialScale
                history.cropRectInWrapper = Rect(gesture_crop_image_view.cropRectInWrapper)
                appliedHistory.add(history)
                appliedRedoHistory.clear()
            } else if(currPrimaryAction == PRIMARY_ACTION_FILTER) {
                if (isFilterApplied) {
                    // ???????????? downsample?????? ???????????? ?????????????????? ??????????????? ????????? ????????? ??????
                    val history = AppliedValueHistory()
                    history.historyType = ACTION_FILTER
                    history.filterConfigStr = (bottom_actions_filter_rv.adapter as FiltersAdapter).getCurrentFilter().filter.second
                    appliedHistory.add(history)
                    appliedRedoHistory.clear()
                }
            } else if(currPrimaryAction == PRIMARY_ACTION_ADJUST) {
                if (isAdjustApplied) {
                    // ???????????? downsample?????? ???????????? ?????????????????? ??????????????? ????????? ????????? ??????
                    var adjustStr = ""
                    for( adjustItem in appliedAdjusts) adjustStr += adjustItem.mConfigStr
                    val history = AppliedValueHistory()
                    history.historyType = ACTION_ADJUST
                    history.adjustConfigStr = adjustStr
                    appliedHistory.add(history)
                    appliedRedoHistory.clear()
                }
            }
            progress_spinner.smoothToShow()
            ensureBackgroundThread {
                // downsample??? ???????????? ????????? ????????? ????????????
                val options = RequestOptions()
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .downsample(DownsampleStrategy.NONE)

                Glide.with(this)
                        .asBitmap()
                        .apply(options)
                        .load(uri).listener(object : RequestListener<Bitmap> {
                            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>?, isFirstResource: Boolean): Boolean {
                                showErrorToast(e.toString())
                                return false
                            }

                            override fun onResourceReady(originBmp: Bitmap?, model: Any?, target: Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                                originalBitmap = originBmp
                                repeatHistoryForMaster()
                                return false
                            }
                        }).submit().get()
            }
        }
    }

    /**
     * ????????? {@link appliedHistory}??? ???????????? ?????????????????? ?????? ??????
     */
    private fun repeatHistoryForMaster() {
        if (appliedHistory.size == 0) {
            saveBitmap(originalBitmap!!)
            return
        }
        val history = appliedHistory[0]

        when(history.historyType)  {
            ACTION_CROP-> {
                val cropListener = CropImageView.OnCropImageCompleteListener { result ->
                    originalBitmap = result

                    // ????????? history??? ????????? ???????????? ????????? ??????
                    if (appliedHistory.size > 1) {
                        appliedHistory.removeAt(0)
                        repeatHistoryForMaster()
                    } else saveBitmap(originalBitmap!!)
                }

                // ??????????????? ??????
                val rect = history.cropRectInWrapper
                rect.set(
                        (rect.left * scaleRateWithMaster).toInt(),
                        (rect.top * scaleRateWithMaster).toInt(),
                        (rect.right * scaleRateWithMaster).toInt(),
                        (rect.bottom * scaleRateWithMaster).toInt())

                val angle = history.cropAngle
                val scaleX = history.cropScaleX
                val scaleY = history.cropScaleY

                // ????????? ???????????? callback??????
                gesture_crop_image_view.setOnCropImageCompleteListener(cropListener)
                // ????????? ??????
                gesture_crop_image_view.getMasterCroppedImageAsync(originalBitmap, rect, angle, scaleX, scaleY)
            }
            ACTION_FILTER -> {
                // Filter ??????
                val ruleString = history.filterConfigStr
                originalBitmap = CGENativeLibrary.filterImage_MultipleEffects(originalBitmap, ruleString, 1f)

                // ????????? history??? ????????? ???????????? ????????? ??????
                if (appliedHistory.size > 1) {
                    appliedHistory.removeAt(0)
                    repeatHistoryForMaster()
                } else saveBitmap(originalBitmap!!)
            }

            ACTION_ADJUST -> {
                // Adjust ??????
                val ruleString = history.adjustConfigStr
                originalBitmap = CGENativeLibrary.filterImage_MultipleEffects(originalBitmap, ruleString, 1f)

                // ????????? history??? ????????? ???????????? ????????? ??????
                if (appliedHistory.size > 1) {
                    appliedHistory.removeAt(0)
                    repeatHistoryForMaster()
                } else saveBitmap(originalBitmap!!)
            }

            ACTION_DOODLE -> {
                val mDoodleBitmapCanvas = Canvas(originalBitmap!!)

                // doodleItems ??? ??????????????? canvas??? ?????? ????????????
                val items = history.doodleItems
                for (item in items) {
                    val location = item.location

                    // item??? boundingRect?????? location??? top-left, pivot??? ???????????????.
                    val width = (item.pivotX - location.x) * 2
                    val height = (item.pivotY - location.y) * 2
                    item.setLocation(location.x * scaleRateWithMaster + width * scaleRateWithMaster / 2 - width / 2, location.y * scaleRateWithMaster + height * scaleRateWithMaster / 2 - height / 2, true)
                    item.pivotX = item.location.x + width / 2
                    item.pivotY = item.location.y + height / 2
                    item.scale = item.scale * scaleRateWithMaster
                    item.draw(mDoodleBitmapCanvas) // set current brush bitmap
                }

                // ????????? history??? ????????? ???????????? ????????? ??????
                if (appliedHistory.size > 1) {
                    appliedHistory.removeAt(0)
                    repeatHistoryForMaster()
                } else saveBitmap(originalBitmap!!)
            }
            ACTION_MOSAIC -> {
                val mDoodleBitmapCanvas = Canvas(originalBitmap!!)

                // doodleItems ??? ??????????????? canvas??? ?????? ????????????
                val items = history.mosaicItems
                for (item in items) {
                    val location = item.location

                    // item??? boundingRect?????? location??? top-left, pivot??? ???????????????.
                    val width = (item.pivotX - location.x) * 2
                    val height = (item.pivotY - location.y) * 2

                    item.setLocation(location.x * scaleRateWithMaster + width * scaleRateWithMaster / 2 - width / 2, location.y * scaleRateWithMaster + height * scaleRateWithMaster / 2 - height / 2, true)
                    item.pivotX = item.location.x + width / 2
                    item.pivotY = item.location.y + height / 2
                    item.scale = item.scale * scaleRateWithMaster
                    item.draw(mDoodleBitmapCanvas) // set current brush bitmap
                }

                // ????????? history??? ????????? ???????????? ????????? ??????
                if (appliedHistory.size > 1) {
                    appliedHistory.removeAt(0)
                    repeatHistoryForMaster()
                } else saveBitmap(originalBitmap!!)
            }
            ACTION_DRAW -> {
                val mDoodleBitmapCanvas = Canvas(originalBitmap!!)

                // doodleItems ??? ??????????????? canvas??? ?????? ????????????
                val items = history.drawItems
                for (item in items) {
                    val location = item.location

                    // item??? boundingRect?????? location??? top-left, pivot??? ???????????????.
                    val width = (item.pivotX - location.x) * 2
                    val height = (item.pivotY - location.y) * 2

                    item.setLocation(location.x * scaleRateWithMaster + width * scaleRateWithMaster / 2 - width / 2, location.y * scaleRateWithMaster + height * scaleRateWithMaster / 2 - height / 2, true)
                    item.pivotX = item.location.x + width / 2
                    item.pivotY = item.location.y + height / 2
                    item.scale = item.scale * scaleRateWithMaster
                    item.draw(mDoodleBitmapCanvas) // set current brush bitmap
                }

                // ????????? history??? ????????? ???????????? ????????? ??????
                if (appliedHistory.size > 1) {
                    appliedHistory.removeAt(0)
                    repeatHistoryForMaster()
                } else saveBitmap(originalBitmap!!)
            }
        }
    }

    /**
     * ????????? ??????????????? ???????????? OutputStream ??????????????? (saveBitmapToFile) ????????????
     * @param bitmap ??????????????? bitmap
     */
    private fun saveBitmap(bitmap: Bitmap) {
        val savePath = getNewFilePath()
        ensureBackgroundThread {
            try {
                saveBitmapToFile(bitmap, savePath)
            } catch (e: OutOfMemoryError) {
                toast(R.string.out_of_memory_error)
            }
        }
    }

    private fun setupBottomActions() {
        setupPrimaryActionButtons()
        setupCropActionButtons()
        setupMoreDrawButtons()
    }

    /**
     *  Crop/Filter/Adjust/More tab?????? listener ??????
     */
    private fun setupPrimaryActionButtons() {
        bottom_primary_crop.setOnClickListener {
            bottomCropClicked()
        }

        bottom_primary_filter.setOnClickListener {
            bottomFilterClicked()
        }

        bottom_primary_adjust.setOnClickListener {
            bottomAdjustClicked()
        }

        bottom_primary_more.setOnClickListener {
            bottomMoreClicked()
        }
    }

    /**
     *  Filter ??????
     */
    private fun bottomFilterClicked() {
        // Filter??? ?????????????????? ?????? ??????
        currPrimaryAction =  PRIMARY_ACTION_FILTER
        updatePrimaryActionButtons()
    }

    /**
     *  Crop ??????
     */
    private fun bottomCropClicked() {
        // Crop??? ?????????????????? ?????? ??????
        currPrimaryAction = PRIMARY_ACTION_CROP
        updatePrimaryActionButtons()
    }

    /**
     *  Adjust ??????
     */
    private fun bottomAdjustClicked() {
        // Adjust ?????????????????? ?????? ??????
        currPrimaryAction =  PRIMARY_ACTION_ADJUST
        updatePrimaryActionButtons()
    }

    /**
     *  More ??????
     */
    private fun bottomMoreClicked() {
        // More??? ?????????????????? ?????? ??????
        currPrimaryAction = PRIMARY_ACTION_MORE
        updatePrimaryActionButtons()
    }

    // Crop??? ?????? ?????? ?????????????????? ?????????????????? ????????????
    var mIsRotateOrFlipAnimating = false;
    /**
     *  Crop??? ??????/????????? ?????? ??????
     */
    private fun setupCropActionButtons() {
        // Crop??? ??????????????? ????????????
        crop_rotate.setOnClickListener {
            if (!mIsRotateOrFlipAnimating) {
                var deltaScale = 1f;
                var deltaX = 0f;
                var deltaY = 0f;
                val startOriginRect = RectF()
                val endOriginRect = RectF()

                val aspectRatio = if (currAspectRatio.first < 0 && currAspectRatio.second < 0 ) { -1f }
                else { currAspectRatio.first / currAspectRatio.second }

                mAnimator.addAnimatorListener(object : SimpleValueAnimatorListener {
                    override fun onAnimationStarted() {
                        // ?????? ????????? matrix ??????
                        gesture_crop_image_view.tempMatrix()

                        // ??????/?????????????????? delta??????
                        val currentScale = gesture_crop_image_view.currentScale
                        if (gesture_crop_image_view.isPortrait) deltaScale = (gesture_crop_image_view.originScalePortrait / currentScale) - 1
                        else deltaScale = (gesture_crop_image_view.originScaleLandscape / currentScale) - 1

                        // ??????/???????????? ??????????????? delta??????
                        val initialCenter = RectUtils.getCenterFromRect(gesture_crop_image_view.maxRect)
                        val currentCenter = gesture_crop_image_view.currentImageCenter

                        deltaX = (initialCenter[0] - currentCenter[0] - gesture_crop_image_view.paddingLeft)
                        deltaY = (initialCenter[1] - currentCenter[1] - gesture_crop_image_view.paddingTop)

                        // ??????????????? animation??? ???????????? ??????
                        startOriginRect.set(crop_view_overlay.noPaddingCropViewRect)
                        // ??????????????? animation??? ???????????? ????????? ???????????? origin(??????) ?????? ??????
                        if (gesture_crop_image_view.isPortrait) endOriginRect.set(gesture_crop_image_view.originRectLandscape)
                        else endOriginRect.set(gesture_crop_image_view.originRectPortrait)

                        // ?????? ????????? ?????? true ??????
                        mIsRotateOrFlipAnimating = true;
                    }

                    override fun onAnimationUpdated(scale: Float) {
                        // ????????????
                        gesture_crop_image_view.rotate(scale);

                        // ??????/?????? ??????
                        gesture_crop_image_view.scale(1f + deltaScale * scale, 1f + deltaScale * scale)

                        // ??????/?????? ???????????? ??????
                        gesture_crop_image_view.postTranslate(deltaX, deltaY)

                        // ?????????????????? animation ??????
                        crop_view_overlay.animateCropViewRect(startOriginRect, endOriginRect, scale, aspectRatio, true, false)
                    }

                    override fun onAnimationFinished() {
                        gesture_crop_image_view.orientationChanged()

                        // ????????? ?????? ????????? ?????????????????? ?????? ??????
                        if (gesture_crop_image_view.isPortrait) crop_view_overlay.setOriginCropRect(gesture_crop_image_view.originRectPortrait)
                        else crop_view_overlay.setOriginCropRect(gesture_crop_image_view.originRectLandscape)

                        // ???????????? ?????? ?????????????????? ?????? ?????????
                        updateAspectRatio()

                        // ?????? ????????? ?????? false ??????
                        mIsRotateOrFlipAnimating = false
                    }
                })
                mAnimator.startAnimation(200)
            }
        }

        // crop??? ?????????????????? ????????????
        crop_flip.setOnClickListener {
            if (!mIsRotateOrFlipAnimating) {
                var flipX = false
                var deltaScale = 1f
                var deltaX = 0f;
                var deltaY = 0f;

                val startOriginRect = RectF()
                val endOriginRect = RectF()
                val aspectRatio = if (currAspectRatio.first < 0 && currAspectRatio.second < 0 ) { -1f }
                else { currAspectRatio.first / currAspectRatio.second }

                mAnimator.addAnimatorListener(object : SimpleValueAnimatorListener {
                    override fun onAnimationStarted() {
                        // ????????? ????????? ?????? true ??????
                        mIsRotateOrFlipAnimating = true;
                        // ?????? ????????? matrix ??????
                        gesture_crop_image_view.tempMatrix()

                        // ????????? ??????
                        flipX = (gesture_crop_image_view.currentScaleX > 0 && gesture_crop_image_view.currentScaleY > 0) ||
                                (gesture_crop_image_view.currentScaleX < 0 && gesture_crop_image_view.currentScaleY < 0)

                        // ??????/?????????????????? delta??????
                        deltaScale = if (gesture_crop_image_view.isPortrait) gesture_crop_image_view.findScaleToWrapRectBound(gesture_crop_image_view.originRectPortrait) - 1
                        else gesture_crop_image_view.findScaleToWrapRectBound(gesture_crop_image_view.originRectLandscape) - 1

                        // ???????????? ?????? ??????/???????????? ??????????????? delta??????
                        val initialCenter = RectUtils.getCenterFromRect(gesture_crop_image_view.maxRect)
                        val currentCenter = gesture_crop_image_view.currentImageCenter

                        if (flipX) {
                            deltaX = - initialCenter[0] + currentCenter[0] + gesture_crop_image_view.paddingLeft
                            deltaY = initialCenter[1] - currentCenter[1] - gesture_crop_image_view.paddingTop
                        } else {
                            deltaX = initialCenter[0] - currentCenter[0] - gesture_crop_image_view.paddingLeft
                            deltaY = -initialCenter[1] + currentCenter[1] + gesture_crop_image_view.paddingTop
                        }

                        // ??????????????? animation??? ???????????? ??????
                        startOriginRect.set(crop_view_overlay.noPaddingCropViewRect)
                        // ??????????????? animation??? ???????????? ????????? ???????????? origin(??????) ?????? ??????
                        if (gesture_crop_image_view.isPortrait) endOriginRect.set(gesture_crop_image_view.originRectPortrait)
                        else endOriginRect.set(gesture_crop_image_view.originRectLandscape)
                    }

                    override fun onAnimationUpdated(scale: Float) {
                        gesture_crop_image_view.resetMatrixByTemp();
                        if (flipX) gesture_crop_image_view.scale(1 - (2 + deltaScale) * scale, 1f + deltaScale * scale)
                        else gesture_crop_image_view.scale(1f + deltaScale * scale, 1 - (2 + deltaScale) * scale)

                        gesture_crop_image_view.postTranslate(deltaX * scale * (deltaScale + 1), deltaY * scale * (deltaScale + 1))

                        // animate crop rect
                        crop_view_overlay.animateCropViewRect(startOriginRect, endOriginRect, scale, aspectRatio, true, false)
                    }

                    override fun onAnimationFinished() {
                        // ???????????? ?????? ?????????????????? ?????? ?????????
                        updateAspectRatio()

                        // ?????????????????? ????????? Flip?????? ?????? ???????????? ??????.
                        val resetAngle = getResetAngle();
                        straight_ruler.setValue(-resetAngle);

                        // ????????? ????????? ?????? false ??????
                        mIsRotateOrFlipAnimating = false
                    }
                })
                mAnimator.startAnimation(300)
            }
        }
    }

    /**
     *  Draw??? ???????????? listener ??????
     */
    private fun setupMoreDrawButtons() {
        more_draw_style_rect.setOnClickListener {
            currMoreDrawStyle = MORE_DRAW_STYLE_RECT
            updateDrawStyle(currMoreDrawStyle)
            highlightDrawStyleButtonBorder()
        }
        more_draw_style_circle.setOnClickListener {
            currMoreDrawStyle = MORE_DRAW_STYLE_CIRCLE
            updateDrawStyle(currMoreDrawStyle)
            highlightDrawStyleButtonBorder()
        }
        more_draw_style_blur_circle.setOnClickListener {
            currMoreDrawStyle = MORE_DRAW_STYLE_BLUR_CIRCLE
            updateDrawStyle(currMoreDrawStyle)
            highlightDrawStyleButtonBorder()
        }
        more_draw_style_blur_brush.setOnClickListener {
            currMoreDrawStyle = MORE_DRAW_STYLE_BLUR_BRUSH
            updateDrawStyle(currMoreDrawStyle)
            highlightDrawStyleButtonBorder()
        }
        more_draw_style_blur_dots.setOnClickListener {
            currMoreDrawStyle = MORE_DRAW_STYLE_BLUR_DOTS
            updateDrawStyle(currMoreDrawStyle)
            highlightDrawStyleButtonBorder()
        }

        // draw??? ??????????????? ?????? ?????????
        more_draw_thickness_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(p0: SeekBar?, i: Int, p2: Boolean) {
                updateDrawThickness(i)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                // ?????? ????????? ???????????? ??? ????????????
                more_draw_thickness_circle.beVisible()
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                // ?????? ????????? ???????????? ??? ?????????
                more_draw_thickness_circle.beGone()
            }
        })

        // draw??? 1?????? ????????????
        more_draw_color_red.setOnClickListener {
            config.lastEditorDrawColorNum = 0;
            updateDrawColor(MORE_DRAW_COLOR_0)
            highlightDrawColorButtonBorder()
        }
        // draw??? 2?????? ????????????
        more_draw_color_green.setOnClickListener {
            config.lastEditorDrawColorNum = 1;
            updateDrawColor(MORE_DRAW_COLOR_1)
            highlightDrawColorButtonBorder()
        }
        // draw??? 3?????? ????????????
        more_draw_color_blue.setOnClickListener {
            config.lastEditorDrawColorNum = 2;
            updateDrawColor(MORE_DRAW_COLOR_2)
            highlightDrawColorButtonBorder()
        }
        // draw??? 4?????? ????????????
        more_draw_color_black.setOnClickListener {
            config.lastEditorDrawColorNum = 3;
            updateDrawColor(MORE_DRAW_COLOR_3)
            highlightDrawColorButtonBorder()
        }
        // draw??? 5?????? ????????????
        more_draw_color_white.setOnClickListener {
            config.lastEditorDrawColorNum = 4;
            updateDrawColor(MORE_DRAW_COLOR_4)
            highlightDrawColorButtonBorder()
        }

        // draw??? ????????????????????? ?????? ??????
        more_draw_color_picker.setOnClickListener {
            openColorPicker()
        }
    }

    /**
     * Draw?????? ????????? ??????????????? ????????? ???????????? ?????? ????????? ??????????????? ??????
     */
    @SuppressLint("ResourceType")
    private fun highlightDrawStyleButtonBorder() {
        // ?????? ???????????? ????????? ????????????
        arrayOf(more_draw_style_rect, more_draw_style_circle, more_draw_style_blur_circle, more_draw_style_blur_brush, more_draw_style_blur_dots).forEach {
            it?.setBackgroundResource(Color.TRANSPARENT)
        }

        // ??????????????? ??????????????? ???????????? ??????
        val currentAspectRatioButton = when (currMoreDrawStyle) {
            MORE_DRAW_STYLE_RECT -> more_draw_style_rect
            MORE_DRAW_STYLE_CIRCLE -> more_draw_style_circle
            MORE_DRAW_STYLE_BLUR_CIRCLE -> more_draw_style_blur_circle
            MORE_DRAW_STYLE_BLUR_BRUSH -> more_draw_style_blur_brush
            else -> more_draw_style_blur_dots
        }
        // ????????? ??????????????? ????????? ????????????
        currentAspectRatioButton?.setBackgroundResource(R.drawable.button_wh_border_background)
    }

    /**
     * Draw?????? ????????? ???????????? ????????? ???????????? ?????? ????????? ??????????????? ??????
     */
    @SuppressLint("ResourceType")
    private fun highlightDrawColorButtonBorder() {
        // ?????? ????????? ????????? ????????????
        arrayOf(more_draw_color_red, more_draw_color_green, more_draw_color_blue, more_draw_color_black, more_draw_color_white).forEach {
            it?.setBackgroundResource(Color.TRANSPARENT)
        }

        // ??????????????? ??????????????? ????????? ??????
        val currentDrawColorButton = when (config.lastEditorDrawColorNum) {
            0-> more_draw_color_red
            1 -> more_draw_color_green
            2 -> more_draw_color_blue
            3 -> more_draw_color_black
            else -> more_draw_color_white
        }
        // ????????? ???????????? ????????? ????????????
        currentDrawColorButton?.setBackgroundResource(R.drawable.button_wh_border_background)
    }

    /**
     * Draw?????? ??????????????? ??????
     * ????????????????????? ????????? ???????????? ?????? ?????????????????? ???????????? ????????? ????????????
     */
    private fun openColorPicker() {
        ColorPickerDialog(this, currMoreDrawColor) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                updateDrawColor(color)

                when (config.lastEditorDrawColorNum) {
                    0->{
                        more_draw_color_red.applyColorFilter(color)
                        MORE_DRAW_COLOR_0 = color
                    }
                    1->{
                        more_draw_color_green.applyColorFilter(color)
                        MORE_DRAW_COLOR_1 = color
                    }
                    2->{
                        more_draw_color_blue.applyColorFilter(color)
                        MORE_DRAW_COLOR_2 = color
                    }
                    3->{
                        more_draw_color_black.applyColorFilter(color)
                        MORE_DRAW_COLOR_3 = color
                    }
                    4->{
                        more_draw_color_white.applyColorFilter(color)
                        MORE_DRAW_COLOR_4 = color
                    }
                }
            }
        }
    }

    /**
     *  ????????? ????????? ?????? ?????????????????? ??????
     */
    @SuppressLint("ResourceType")
    private fun updatePrimaryActionButtons() {
        // ??????????????? ?????? ???????????????, ???????????? ??????????????? ????????????
        if (currPrimaryAction == prePrimaryAction || mCropInProgress) return

        // ????????? ????????? ?????? ??????
        if (currPrimaryAction == PRIMARY_ACTION_CROP) {
            loadCropImageView()
        } else if (currPrimaryAction == PRIMARY_ACTION_FILTER) {
            loadFilterImageView()
        } else if (currPrimaryAction == PRIMARY_ACTION_ADJUST) {
            loadAdjustImageView()
        } else if (currPrimaryAction == PRIMARY_ACTION_MORE) {
            loadMoreImageView()
        }
    }

    /**
     * Crop/Filter/Adjust/More ????????? ??????????????? ???????????? UI ??????
     */
    private fun updateUI() {
        if (currPrimaryAction == PRIMARY_ACTION_CROP) {
            ucrop_view.beVisible()
            default_image_view_container.beGone()
            adjust_view_container.beInvisible()
            doodle_image_view.beGone()
            rotate_reset.beGone()

            txt_adjust_alert.beGone()
            txt_filter_alert.beGone()

            straight_ruler.setValue(0f)
        } else if (currPrimaryAction == PRIMARY_ACTION_FILTER) {
            default_image_view_container.beVisible()
            doodle_image_view.beGone()
            adjust_view_container.beInvisible()
            ucrop_view.beGone()

            txt_adjust_alert.beGone()
        } else if (currPrimaryAction == PRIMARY_ACTION_ADJUST) {
            adjust_view_container.beVisible()
            adjust_view.beVisible()
            default_image_view_container.beGone()
            ucrop_view.beGone()
            doodle_image_view.beGone()
            rgb_curve_canvas.beGone()
            rgb_channel_spinner.beGone()

            txt_filter_alert.beGone()
        } else if (currPrimaryAction == PRIMARY_ACTION_MORE) {
            default_image_view_container.beVisible()
            doodle_image_view.beGone()
            ucrop_view.beGone()
            adjust_view_container.beInvisible()

            txt_adjust_alert.beGone()
            txt_filter_alert.beGone()
        }

        // ????????? ??????????????? ?????? isSelected????????? ???????????? ??????????????? ???????????? ??????(?????????)
        val currentPrimaryActionButton = when (currPrimaryAction) {
            PRIMARY_ACTION_FILTER -> bottom_primary_filter
            PRIMARY_ACTION_CROP -> bottom_primary_crop
            PRIMARY_ACTION_ADJUST -> bottom_primary_adjust
            PRIMARY_ACTION_MORE -> bottom_primary_more
            else -> null
        }

        arrayOf(bottom_primary_filter, bottom_primary_crop, bottom_primary_adjust, bottom_primary_more).forEach {
            it?.isSelected = false
        }
        currentPrimaryActionButton?.isSelected = true

        preBottomAccordian.beGone()
        // ??????????????? ????????????????????? ????????? ?????????
        btn_compare.beGone()
        when (currPrimaryAction) {
            PRIMARY_ACTION_CROP -> {
                bottom_editor_crop_actions.beVisible()
                preBottomAccordian = bottom_editor_crop_actions;
                prePrimaryAction = PRIMARY_ACTION_CROP
            }
            PRIMARY_ACTION_FILTER -> {
                bottom_editor_filter_actions.beVisible()
                btn_compare.beVisible()
                preBottomAccordian = bottom_editor_filter_actions;
                prePrimaryAction = PRIMARY_ACTION_FILTER
            }
            PRIMARY_ACTION_ADJUST -> {
                bottom_editor_adjust_actions.beVisible()
                btn_compare.beVisible()
                preBottomAccordian = bottom_editor_adjust_actions;
                prePrimaryAction = PRIMARY_ACTION_ADJUST
            }
            PRIMARY_ACTION_MORE -> {
                bottom_editor_more_actions.beVisible()
                btn_compare.beVisible()
                preBottomAccordian = bottom_editor_more_actions;
                prePrimaryAction = PRIMARY_ACTION_MORE
            }
        }

        // ?????? View??? animation??????
        controlBarAnim(bottom_bar_container);
    }

    /**
     * ????????? ??????????????? ?????? ?????? View??? animation ??????
     * @param view  animation??? ???????????? View
     */
    private fun controlBarAnim(view: View) {
        // set bottom bars animation
        val fadeIn = AlphaAnimation(0f, 1f);
        val transIn = TranslateAnimation(0f, 0f, 100f, 0f)

        val animInSet = AnimationSet(true)
        animInSet.addAnimation(fadeIn)
        animInSet.addAnimation(transIn)
        animInSet.duration = 300

        view.startAnimation(animInSet)
    }

    // filter??? ????????????????????? ????????????
    private var isFilterApplied = false

    /**
     * Filter?????? ???????????? RecyclerView Adapter??? ????????????
     */
    private fun generateFilterRVAdapter() {
        ensureBackgroundThread {
            val thumbSquare = getFilterThumb()
            runOnUiThread {
                val filterItems = ArrayList<FilterItem>()

                // ???????????? ?????? FilterItem ??????
                val noFilter = Pair(R.string.original, "")
                filterItems.add(FilterItem(thumbSquare, noFilter))

                // filterItems ????????? FilterItem?????? ??????
                getFilterPack()!!.forEach {
                    val filterItem = FilterItem(thumbSquare, it)
                    filterItems.add(filterItem)
                }

                // Filter RecyclerView Adapter ??????
                val adapter = FiltersAdapter(applicationContext, filterItems) {
                    // click listener
                    if (!mCropInProgress) {
                        // filter????????? ?????? animation ??????
                        itemNameAlertAnim(txt_filter_alert, getString(filterItems[it].filter.first))
                        isFilterApplied = it > 0
                        applyFilter(filterItems[it])
                    }
                }

                bottom_actions_filter_rv.adapter = adapter
                adapter.notifyDataSetChanged()
            }
        }
    }

    /**
     * Filter/Adjust??? ???????????? ????????? ???????????? ?????? animation ??????
     * @param textView  animation??? ???????????? TextView
     * @param filterName  ???????????? ??????/???
     */
    private fun itemNameAlertAnim(textView: TextView, filterName: String) {
        val scaleUp = ScaleAnimation(0.5f, 1f, 0.5f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f )
        val fadeIn = AlphaAnimation(0f, 1f)

        val animSet = AnimationSet(true)
        animSet.addAnimation(scaleUp)
        animSet.addAnimation(fadeIn)
        animSet.duration = 200

        animSet.setAnimationListener(object: Animation.AnimationListener{
            override fun onAnimationRepeat(p0: Animation?) { }

            override fun onAnimationEnd(p0: Animation?) {
                text_alert_handler.postDelayed(Runnable {
                    textView.beGone()
                }, 800)
            }

            override fun onAnimationStart(p0: Animation?) {
                textView.text = filterName
                textView.beVisible()
            }
        })
        text_alert_handler.removeCallbacksAndMessages(null)
        textView.clearAnimation()
        textView.beGone()
        textView.startAnimation(animSet)
    }

    /**
     * Adjust?????? ???????????? RecyclerView Adapter??? ????????????
     */
    private fun generateAdjustRVAdapter() {
        ensureBackgroundThread {
            runOnUiThread {

                // Adjust ?????? ??????
                val adjustList: ArrayList<AdjustConfig> = ArrayList()
                adjustList.add( AdjustConfig(R.string.Exposure, R.drawable.ic_adjust_exposure_selector, "adjust", "exposure", -2f, 2f, 0f) )
                adjustList.add( AdjustConfig(R.string.Contrast, R.drawable.ic_adjust_contrast_selector, "adjust", "contrast", 0.1f, 3f, 1f) )
                adjustList.add( AdjustConfig(R.string.Saturation, R.drawable.ic_adjust_exposure_selector, "adjust", "saturation", 0f, 3f, 1f) )
                adjustList.add( AdjustConfig(R.string.Shadow, R.drawable.ic_adjust_shadow_selector, "adjust", "shadow", -200f, 100f, 0f) )
                adjustList.add( AdjustConfig(R.string.Highlight, R.drawable.ic_adjust_highlights_selector, "adjust", "highlight", -100f, 200f, 0f) )
                adjustList.add( AdjustConfig(R.string.Hue, R.drawable.ic_adjust_hue_selector, "adjust", "hue", -5f, 5f, 0f) )         // 0 - 359, 0
                adjustList.add( AdjustConfig(R.string.Temperature, R.drawable.vector_adjust_temperature, "adjust", "whitebalance", -0.8f, 0.8f, 0f) )         // 0 - 359, 0
                adjustList.add( AdjustConfig(R.string.Posterize, R.drawable.ic_adjust_posterize_selector, "adjust", "posterize", 1f, 256f, 30f) )   // 1 - 256, 10
                adjustList.add( AdjustConfig(R.string.Vibrance, R.drawable.ic_gallery_adjust_vibrance, "adjust", "vibrance", -1f, 1f, 0f) )     // any

                adjustList.add( AdjustConfig(R.string.Curve, R.drawable.ic_adjust_curves_selector, "curve", "curve", 0f, 0f, 0f) )
//                adjustList.add( AdjustConfig(R.string.Edge, R.drawable.ic_adjust_bwfilter_selector, "style", "edge", 0f, 10f, 5f) )

                val adapter = AdjustAdapter(applicationContext, adjustList) {
                    if (!mCropInProgress) {
                        val adjustItem = adjustList.get(it)
                        // adjust?????? animation ??????
                        itemNameAlertAnim(txt_adjust_alert, getString(adjustItem.mNameResID))

                        updateAdjustSeekbar(adjustItem)
                    }
                }

                bottom_actions_adjust_rv.adapter = adapter
                adapter.notifyDataSetChanged()
            }
        }
    }

    /**
     * ????????????????????? ???????????? ???????????? RecyclerView Adapter ?????? (e.g. 16:9, 1:1, 4:3, etc)
     */
    private fun generateCropRVAdapter() {
        ensureBackgroundThread {
            runOnUiThread {
                val width: Int = realScreenSize.x
                val height: Int = realScreenSize.y

                val cropItemList: ArrayList<CropSelectItem> = ArrayList()
                cropItemList.add(CropSelectItem(R.drawable.baseline_image_aspect_ratio_white_48, -1f, -1f))
                cropItemList.add(CropSelectItem(R.drawable.baseline_crop_portrait_white_48, width.toFloat(), height.toFloat()))
                cropItemList.add(CropSelectItem(R.drawable.baseline_crop_landscape_white_48, height.toFloat(), width.toFloat()))
                cropItemList.add(CropSelectItem(R.drawable.baseline_crop_1_1_white_48, 1f, 1f))
                cropItemList.add(CropSelectItem(R.drawable.baseline_crop_16_9_white_48, 16f, 9f))
                cropItemList.add(CropSelectItem(R.drawable.baseline_crop_9_16_white_48, 9f, 16f))
                cropItemList.add(CropSelectItem(R.drawable.baseline_crop_4_3_white_48, 4f, 3f))
                cropItemList.add(CropSelectItem(R.drawable.baseline_crop_3_4_white_48, 3f, 4f))
                cropItemList.add(CropSelectItem(R.drawable.baseline_crop_3_2_white_48, 3f, 2f))
                cropItemList.add(CropSelectItem(R.drawable.baseline_crop_2_3_white_48, 2f, 3f))

                val adapter = CropSelectorRVAdapter(applicationContext, cropItemList) {pos: Int, animate: Boolean ->
                    // pos: RecyclerView?????? ????????? ?????? ??????
                    // animate: ?????????????????? animation??? ??????????????? ???????????? ????????????

                    val cropItem = cropItemList.get(pos)
                    currAspectRatio = Pair(cropItem.x, cropItem.y)

                    if (animate) {
                        // ?????????????????? animation ??????
                        if (!mIsRotateOrFlipAnimating) {
                            val startOriginRect = RectF()
                            val endOriginRect = RectF()
                            val aspectRatio = if (currAspectRatio.first < 0 && currAspectRatio.second < 0) {
                                -1f
                            } else {
                                currAspectRatio.first / currAspectRatio.second
                            }
                            mAnimator.addAnimatorListener(object : SimpleValueAnimatorListener {
                                override fun onAnimationStarted() {
                                    // ??????????????? animation??? ???????????? ??????
                                    startOriginRect.set(crop_view_overlay.noPaddingCropViewRect)
                                    // ??????????????? animation??? ???????????? ????????? ???????????? origin(??????) ?????? ??????
                                    if (gesture_crop_image_view.isPortrait) endOriginRect.set(gesture_crop_image_view.originRectPortrait)
                                    else endOriginRect.set(gesture_crop_image_view.originRectLandscape)

                                    mIsRotateOrFlipAnimating = true;
                                }

                                override fun onAnimationUpdated(scale: Float) {
                                    // animate crop rect
                                    crop_view_overlay.animateCropViewRect(startOriginRect, endOriginRect, scale, aspectRatio, true, false)
                                }

                                override fun onAnimationFinished() {
                                    mIsRotateOrFlipAnimating = false
                                    updateAspectRatio()
                                }
                            })
                            mAnimator.startAnimation(200)
                        }
                    } else updateAspectRatio()
                }

                crop_rv.adapter = adapter
                adapter.notifyDataSetChanged()
            }
        }
    }

    /**
     * Adjust??? ???????????? ????????? ????????? ?????????, ?????????????????????
     */
    private fun resetAdjustSavedValues() {
        // clear saved adjust items
        appliedAdjusts.clear()
        (bottom_actions_adjust_rv.adapter as AdjustAdapter).resetItems()
        setAdjustConfigStr()
        isAdjustApplied = false

        // reset rgb curve saved values for chart
        curve_points_rgb.clear()
        curve_points_r.clear()
        curve_points_g.clear()
        curve_points_b.clear()
        rgb_curve_canvas.resetPoints()
    }

    /**
     * Adjust??? ????????? ???????????? ???????????? Seekbar ??????, onSeekChangelistener ??????
     * @param adjustItem  ????????? adjustItem
     */
    private fun updateAdjustSeekbar(adjustItem: AdjustConfig) {

        if (adjustItem.mTypeName == "curve") {
            rgb_curve_canvas.beVisible()
            rgb_channel_spinner.beVisible()

            // curve??? ????????? ??????????????? listener
            rgb_curve_canvas.setPointUpdateListener(object : CanvasSpliner.PointMoveListener {
                // curve??? ?????? ???????????? ?????????
                override fun onCurvePointUpdate(canvasPoints: List<PointF>, curvePoints: List<PointF>) {
                    when (currentRGBChannel) {
                        CURVE_CHANNEL_RGB -> {
                            // RGB channel ??????????????? ??????
                            color_points_rgb = "RGB"
                            for(point in curvePoints) {
                                color_points_rgb += "(" + (255 * point.x).toInt() + ", " + (255 * point.y).toInt() + ") "
                            }
                            // channel??? ????????? ????????? ???????????? ????????? ??????
                            curve_points_rgb = ArrayList(canvasPoints)
                        };
                        CURVE_CHANNEL_R -> {
                            // R channel ??????????????? ??????
                            color_points_r = "R"
                            for(point in curvePoints) {
                                color_points_r += "(" + (255 * point.x).toInt() + ", " + (255 * point.y).toInt() + ") "
                            }
                            // channel??? ????????? ????????? ???????????? ????????? ??????
                            curve_points_r = ArrayList(canvasPoints)
                        };
                        CURVE_CHANNEL_G -> {
                            // G channel ??????????????? ??????
                            color_points_g = "G"
                            for(point in curvePoints) {
                                color_points_g += "(" + (255 * point.x).toInt() + ", " + (255 * point.y).toInt() + ") "
                            }
                            // channel??? ????????? ????????? ???????????? ????????? ??????
                            curve_points_g = ArrayList(canvasPoints)
                        };
                        CURVE_CHANNEL_B -> {
                            // B channel ??????????????? ??????
                            color_points_b = "B"
                            for(point in curvePoints) {
                                color_points_b += "(" + (255 * point.x).toInt() + ", " + (255 * point.y).toInt() + ") "
                            }
                            // channel??? ????????? ????????? ???????????? ????????? ??????
                            curve_points_b = ArrayList(canvasPoints)
                        };
                    }

                    adjustItem.mConfigStr = "@curve " + color_points_rgb + color_points_r + color_points_g + color_points_b

                    if (appliedAdjusts.indexOf(adjustItem) == -1) appliedAdjusts.add(adjustItem)
                    setAdjustConfigStr()
                }

                // curve??? ?????? ???????????? ????????????
                override fun onPointUp() {
                    // ?????? ????????? ??????????????? ????????????
                    var unChanged = true;

                    val width = rgb_curve_canvas.splinerWidth.toFloat()
                    val height = rgb_curve_canvas.splinerHeight.toFloat()

                    if (curve_points_rgb.size == 2) {
                        unChanged = unChanged && curve_points_rgb[0].x == 1f && curve_points_rgb[0].y == 0f &&
                                curve_points_rgb[1].x == width && curve_points_rgb[1].y == height
                    } else unChanged = unChanged && curve_points_rgb.size == 0

                    if (curve_points_r.size == 2) {
                        unChanged = unChanged && curve_points_r[0].x == 1f && curve_points_r[0].y == 0f &&
                                curve_points_r[1].x == width && curve_points_r[1].y == height
                    } else unChanged = unChanged && curve_points_r.size == 0

                    if (curve_points_g.size == 2) {
                        unChanged = unChanged && curve_points_g[0].x == 1f && curve_points_g[0].y == 0f &&
                                curve_points_g[1].x == width && curve_points_g[1].y == height
                    } else unChanged = unChanged && curve_points_g.size == 0

                    if (curve_points_b.size == 2) {
                        unChanged = unChanged &&  curve_points_b[0].x == 1f && curve_points_b[0].y == 0f &&
                                curve_points_b[1].x == width && curve_points_b[1].y == height
                    } else unChanged = unChanged && curve_points_b.size == 0

                    // ????????? ????????? appliedAdjusts?????? ??????
                    if (unChanged) appliedAdjusts.remove(adjustItem)

                    // adjust??? ?????????????????? ?????? ??????
                    isAdjustApplied = appliedAdjusts.size > 0
                }
            })
        } else {
            rgb_curve_canvas.beGone()
            rgb_channel_spinner.beGone()
        }

        // ???????????? ?????? adjust??? ???????????? ??????/???????????? ?????? ????????? (e.g. Curve)
        adjust_seek_bar.beGoneIf(adjustItem.minValue == adjustItem.maxValue)

        adjust_seek_bar.onSeekChangeListener = null
        adjust_seek_bar.min = -50f
        adjust_seek_bar.max = 50f
        adjust_seek_bar.setProgress( adjustItem.slierIntensity * (adjust_seek_bar.max - adjust_seek_bar.min) + adjust_seek_bar.min )
        adjust_seek_bar.onSeekChangeListener = object : OnSeekChangeListener {
            override fun onSeeking(seekParams: SeekParams?) {
                synchronized(this) {
                    val progress = seekParams!!.progress

                    // ??????????????? animation??????
                    itemNameAlertAnim(txt_adjust_alert, progress.toString())

                    val intensity = (progress - adjust_seek_bar.min) / (adjust_seek_bar.max - adjust_seek_bar.min)
                    adjustItem.updateIntensity(intensity);

                    // ??????????????? adjustItem??? ??????????????? appliedAdjusts?????? ???????????? ?????? ??????
                    if (adjustItem.intensity == adjustItem.originvalue) {
                        appliedAdjusts.remove(adjustItem)
                        setAdjustConfigStr()
                        return;
                    } else {
                        // ?????? ????????? adjustItem????????? ???????????? ????????? ???????????? ?????? ??????
                        if (appliedAdjusts.indexOf(adjustItem) == -1) {
                            Log.d("from", "add");
                            appliedAdjusts.add(adjustItem)
                            setAdjustConfigStr()
                            return;
                        }
                    }
                    // ????????? adjustItem?????? ????????? ?????? ??????
                    if (appliedAdjusts.size > 0) {
                        if (adjustItem.mFuncName == "shadowhighlight" || adjustItem.mFuncName == "curve" || adjustItem.mFuncName == "whitebalance") {
                            setAdjustConfigStr()
                        } else {
                            val index = appliedAdjusts.indexOf(adjustItem)
                            if (index != -1) adjust_view.setFilterIntensityForIndex(adjustItem.intensity, index)
                        }
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: IndicatorSeekBar?) { }

            override fun onStopTrackingTouch(seekBar: IndicatorSeekBar?) {
                // adjust??? ?????????????????? ??????
                isAdjustApplied = appliedAdjusts.size > 0
            }
        }
    }

    /**
     * ????????? adjust?????? ??????????????? ????????? ??????
     * ?????? adjust?????? ??????????????? ??????(setFilterIntensityForIndex)??? ??????????????? setFilterWithConfig??? ????????? ??????????????????
     */
    fun setAdjustConfigStr() {
        var groupAdjustStr = ""
        for ( adjustItem in appliedAdjusts) {
            groupAdjustStr += adjustItem.mConfigStr
        }
        adjust_view.setFilterWithConfig(groupAdjustStr)
    }

    // filter??? ????????? ????????? ?????? bitmap
    var lastFilteredBmp : Bitmap? = null

    /**
     * ??????????????? ????????? filterItem??? ???????????? ????????????
     */
    private fun applyFilter(filterItem: FilterItem) {
        if (lastFilteredBmp != null && lastFilteredBmp != currentBitmap) lastFilteredBmp!!.recycle()

        val ruleString = filterItem.filter.second
        val filteredBmp = CGENativeLibrary.filterImage_MultipleEffects(currentBitmap!!, ruleString, 1f)
        default_image_view.setImageBitmap(filteredBmp)
        lastFilteredBmp = filteredBmp
    }

    /**
     * ????????? ????????? ????????? ??????
     */
    private fun updateAspectRatio() {
        if (currAspectRatio!!.first < 0 && currAspectRatio!!.second < 0 ) {
            // ??? ????????? ??????????????????
            gesture_crop_image_view.setKeepAspectRatio(false);
            gesture_crop_image_view.targetAspectRatio = FREE_ASPECT_RATIO
        } else {
            // ??? ????????? ?????????????????? (e.g. 16:9, 3:2, etc)
            gesture_crop_image_view.setKeepAspectRatio(true);
            gesture_crop_image_view.targetAspectRatio = currAspectRatio!!.first / currAspectRatio!!.second
        }
    }

    /**
     * Draw??? ?????????????????? ??????, preference??? ??????
     */
    private fun updateDrawColor(color: Int) {
        currMoreDrawColor = color
        config.lastEditorBrushColor = color
        setDoodleColor(color)
    }

    /**
     * Draw??? ??????????????? ??????, preference??? ??????
     */
    private fun updateDrawStyle(style: Int) {
        config.lastEditorBrushStyle = style
        mDoodle.setBrushBMPStyle(style)
    }

    /**
     * Draw??? ??????????????? ??????, preference??? ??????
     */
    private fun updateDrawThickness(percent: Int) {
        config.lastEditorBrushSize = percent
        val width = resources.getDimension(R.dimen.full_brush_size) * (percent / 100f)
        setDoodleSize(width.toInt());

        val scale = Math.max(0.03f, percent / 100f)
        more_draw_thickness_circle.scaleX = scale
        more_draw_thickness_circle.scaleY = scale
    }

    /**
     * ????????? ???????????? callback
     * @param result  ????????? ????????????
     */
    override fun onCropImageComplete(resultBmp: Bitmap) {
        if (resultBmp != null) {
            if (isCropIntent) {
                if (saveUri.scheme == "file") {
                    saveBitmapToFile(resultBmp, saveUri.path!!)
                } else {
                    var inputStream: InputStream? = null
                    var outputStream: OutputStream? = null
                    try {
                        val stream = ByteArrayOutputStream()
                        resultBmp.compress(CompressFormat.JPEG, 100, stream)
                        inputStream = ByteArrayInputStream(stream.toByteArray())
                        outputStream = contentResolver.openOutputStream(saveUri)
                        inputStream.copyTo(outputStream!!)
                    } finally {
                        inputStream?.close()
                        outputStream?.close()
                    }

                    Intent().apply {
                        data = saveUri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setResult(RESULT_OK, this)
                    }
                    finish()
                }
            } else {
                if (cropForAction == CROP_FOR_SAVE) {
                    // save cropped bitmap
                    saveBitmap(resultBmp)
                    return;
                }
                if (cropForAction == CROP_FOR_COMPARE) {
                    releaseCompareView()
                    compare_image_view.setImageBitmap(resultBmp)
                    historyManager.addCompareHistory(resultBmp)
                } else {
                    currentBitmap!!.recycle()
                    currentBitmap = resultBmp
                    mCropInProgress = false;
                    progress_spinner.smoothToHide()

                    if (cropForAction == CROP_FOR_FILTER) {
                        default_image_view.setImageBitmap(resultBmp)
                        updateFilterThumb()
                    } else if (cropForAction == CROP_FOR_ADJUST) {
                        setAdjustImage(resultBmp)
                    } else if (cropForAction == CROP_FOR_MORE) {
                        default_image_view.setImageBitmap(resultBmp)
                    }
                    releaseCropView()

                    updateUI()

                    // save history
                    makeHistory(resultBmp);

                    // ???????????? ????????? ??????
                    cropForAction = CROP_FOR_COMPARE
                    gesture_crop_image_view.getCroppedImageAsync(compare_image_view.drawable.convertToBitmap())
                }
            }
        } else {
            toast(getString(R.string.image_editing_failed))
        }
    }

    /**
     * HistoryManager??? history ??????????????? ???????????? Undo/Redo ?????????????????? ??????
     * @param history  history??? ??????????????? bitmap
     */
    private fun makeHistory(history: Bitmap) {
        historyManager.addHistory(history);
        updateUndoRedoButton()
    }

    /**
     * Undo/Redo ???????????? ???????????? ??????
     */
    private fun updateUndoRedoButton() {
        if (menu_edit != null ) {
            menu_edit!!.findItem(R.id.menu_undo).isEnabled = historyManager.canUndo()
            menu_edit!!.findItem(R.id.menu_redo).isEnabled = historyManager.canRedo()
        }
    }

    /**
     * ????????? ??????????????? ????????????
     */
    private fun getNewFilePath(): String {
        val folderPath: String = if(realFilePath != ""){
            realFilePath.getParentPath()
        }
        else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/Camera").toString()
        }

        val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "IMG_" + simpleDateFormat.format(Date(System.currentTimeMillis())) + ".jpg"

        val file = File(folderPath, fileName);
        return file.absolutePath
    }

    /**
     * ??????????????? ???????????? OutputStream??? ???????????? ?????????????????? ??????
     * @param bitmap  ??????????????? bitmap
     * @param path  ????????????
     */
    private fun saveBitmapToFile(bitmap: Bitmap, path: String) {
        try {
            ensureBackgroundThread {
                val file = File(path)
                val fileDirItem = FileDirItem(path, path.getFilenameFromPath())
                getFileOutputStream(fileDirItem, true) {
                    if (it != null) {
                        saveBitmap(file, bitmap, it)
                    } else {
                        toast(R.string.image_editing_failed)
                    }
                }
            }
        } catch (e: Exception) {
            showErrorToast(e)
        } catch (e: OutOfMemoryError) {
            toast(R.string.out_of_memory_error)
        }
    }

    /**
     * ?????????????????? <br/>
     * ????????? ???????????? Activity??? ?????????
     * @param file : ???????????? ??????
     * @param bitmap : ??????????????? bitmap
     * @param out : ????????? ???????????? OutputStream
     */
    @TargetApi(Build.VERSION_CODES.N)
    private fun saveBitmap(file: File, bitmap: Bitmap, out: OutputStream) {
        bitmap.compress(file.absolutePath.getCompressionFormat(), 90, out)
        out.close()

        if(file.exists() && file.length() > 0 && file.canRead()) {
            val path = file.absolutePath
            val paths = arrayListOf(path)
            rescanPaths(paths) {
                //Medium????????? ???????????? ????????? intent??? extra????????? ?????????.
                val modified = file.lastModified()
                val newMedium = Medium(null, path.getFilenameFromPath(), path, path.getParentPath(), modified, modified, Utils.formatDate(modified), file.length(), MEDIA_IMAGE, 0, 0)
                intent.putExtra(RESULT_MEDIUM, newMedium)
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }
    }

    fun onClick(v: View) {
        if (v.id == R.id.doodle_selectable_remove) {
            // ????????? ????????? ??????
            mDoodle.removeItem(mTouchGestureListener.selectedItem)
            mTouchGestureListener.selectedItem = null
            updateDoodleUndoRedo()
        } else if (v.id == R.id.doodle_selectable_top) {
            // ????????? ????????? ????????? ?????? (z-order)
            mDoodle.topItem(mTouchGestureListener.selectedItem)
        } else if (v.id == R.id.doodle_selectable_bottom) {
            // ????????? ?????? ???????????? ?????? (z-order)
            mDoodle.bottomItem(mTouchGestureListener.selectedItem)
        } else if (v.id == R.id.btn_paint_brush) {
            // doodle??? ??? ????????? ??????
            mDoodle.shape = DoodleShape.PAINTBRUSH
            loadPreference()
        } else if (v.id == R.id.btn_arrow) {
            // doodle??? ????????? ????????? ??????
            mDoodle.shape = DoodleShape.ARROW
            loadPreference()
        } else if (v.id == R.id.btn_holl_circle) {
            // doodle??? ??? ????????? ??????
            mDoodle.shape = DoodleShape.HOLLOW_CIRCLE
            loadPreference()
        } else if (v.id == R.id.btn_holl_rect) {
            // doodle??? ????????? ????????? ??????
            mDoodle.shape = DoodleShape.HOLLOW_RECT
            loadPreference()
        } else if (v.id == R.id.btn_doodle_size) {
            // doodle??? ????????? ??????
            if (currDoodleSizeSelection == DOODLE_SIZE_1) currDoodleSizeSelection = DOODLE_SIZE_2
            else if (currDoodleSizeSelection == DOODLE_SIZE_2) currDoodleSizeSelection = DOODLE_SIZE_3
            else if (currDoodleSizeSelection == DOODLE_SIZE_3) currDoodleSizeSelection = DOODLE_SIZE_1

            setDoodleSize(currDoodleSizeSelection)
            // preference??? ??????
            editor.putInt(prefKeyBuilder("size"), currDoodleSizeSelection)
            editor.apply()
        } else if (v.id == R.id.btn_mosaic_level_1) {
            // mosaic??? ??????1 ??????
            v.isSelected = true
            btn_mosaic_level_2.isSelected = false
            btn_mosaic_level_3.isSelected = false
            // mosaic??? ??????1 ??????
            mMosaicLevel = DoodlePath.MOSAIC_LEVEL_1
            mDoodle.color = DoodlePath.getMosaicColor(mDoodle, mMosaicLevel)
            if (mTouchGestureListener.selectedItem != null) {
                // ????????? ???????????? ?????? item??? ????????? item??? ????????? ??????
                mTouchGestureListener.selectedItem.color = mDoodle.color.copy()
            }
            // preference??? ??????
            editor.putInt(DOODLE_MOSAIC_LEVEL, DOODLE_SIZE_1)
            editor.apply()
        } else if (v.id == R.id.btn_mosaic_level_2) {
            // mosaic??? ??????2 ??????
            v.isSelected = true
            btn_mosaic_level_1.isSelected = false
            btn_mosaic_level_3.isSelected = false
            // mosaic??? ??????2 ??????
            mMosaicLevel = DoodlePath.MOSAIC_LEVEL_2
            mDoodle.color = DoodlePath.getMosaicColor(mDoodle, mMosaicLevel)
            if (mTouchGestureListener.selectedItem != null) {
                // ????????? ???????????? ?????? item??? ????????? item??? ????????? ??????
                mTouchGestureListener.selectedItem.color = mDoodle.color.copy()
            }
            // preference??? ??????
            editor.putInt(DOODLE_MOSAIC_LEVEL, DOODLE_SIZE_2)
            editor.apply()
        } else if (v.id == R.id.btn_mosaic_level_3) {
            // mosaic??? ??????3 ??????
            v.isSelected = true
            btn_mosaic_level_1.isSelected = false
            btn_mosaic_level_2.isSelected = false
            // mosaic??? ??????3 ??????
            mMosaicLevel = DoodlePath.MOSAIC_LEVEL_3
            mDoodle.color = DoodlePath.getMosaicColor(mDoodle, mMosaicLevel)
            if (mTouchGestureListener.selectedItem != null) {
                // ????????? ???????????? ?????? item??? ????????? item??? ????????? ??????
                mTouchGestureListener.selectedItem.color = mDoodle.color.copy()
            }
            // preference??? ??????
            editor.putInt(DOODLE_MOSAIC_LEVEL, DOODLE_SIZE_3)
            editor.apply()
        }
        if (v.tag != null) {
            val tag = v.tag.toString()
            if (tag.substring(0, 12) == "doodle_color") {
                // doodle??? ???????????? ??????
                // doodle ?????? ??????
                val color = tag.substring(13)
                setDoodleColor(color)
                // doodle ?????? ??????
                editor.putString(prefKeyBuilder("color"), color)
                editor.apply()
            }
        }
    }

    /**
     * doodle??? Undo/Redo ????????? ??????????????? ??????
     */
    private fun updateDoodleUndoRedo() {
        menu_edit!!.findItem(R.id.menu_doodle_undo).isEnabled = mDoodle.itemCount > 0
        menu_edit!!.findItem(R.id.menu_doodle_redo).isEnabled = mDoodle.redoItemCount > 0
    }

    /**
     * Doodle??? ????????? ????????? ????????????.<br/>
     * ??????????????? ??? ??? ???????????? ??? ?????????.
     * @param newSize ????????? ??????
     */
    private fun setDoodleSize(newSize: Int) {
        var size = calcDoodlePaintSize(newSize.toFloat())

        if (mDoodle.shape == DoodleShape.ARROW) {
            // ????????? ???????????? ?????? ????????? ?????? ????????? ????????? ??? ????????????.
            // setSize() ????????? ??????????????? ??????.
            size *= if (newSize == DOODLE_SIZE_1) 2f
            else 1.5f
        }

        if (mDoodle.size != size) {
            mDoodle.size = size
            // ????????? ???????????? ?????? item??? ????????? item??? ????????? ????????????
            if (mTouchGestureListener.selectedItem != null) {
                mTouchGestureListener.selectedItem.size = size
            }
        }
    }

    /**
     * ??????????????? ?????? ?????? ????????? ????????? ???????????? ????????????.
     * @param newSize ?????? ??????????????? ????????? ??????
     */
    private fun calcDoodlePaintSize(newSize: Float) : Float {
        // ??????????????? ???????????? ????????????.
        return currentBitmap!!.width * 2f / realScreenSize.x * newSize
    }

    /**
     * Doodle??? ????????? ????????????
     * @param color ????????? ???????????? hex?????????
     */
    private fun setDoodleColor(color: String) {
        mDoodle.color = DoodleColor(Color.parseColor(color))
    }

    /**
     * Doodle??? ????????? ????????????
     * @param color ??????????????? ?????? (e.g. Color.RED)
     */
    private fun setDoodleColor(color: Int) {
        mDoodle.color = DoodleColor(color)
    }

    /**
     * ???????????? ?????? ????????? preference??? ???????????? key??? ????????????
     */
    fun prefKeyBuilder(suffix: String?): String? {
        if (!mDoodleView.isEditMode) {
            val shape = mDoodle.shape
            val builder = java.lang.StringBuilder("doodle_")
            if (shape === DoodleShape.PAINTBRUSH) builder.append("paint_brush_")
            else if (shape === DoodleShape.HOLLOW_RECT) builder.append("rect_")
            else if (shape === DoodleShape.HOLLOW_CIRCLE) builder.append("circle_")
            else if (shape === DoodleShape.ARROW) builder.append("arrow_")
            return builder.append(suffix).toString()
        } else return ""
    }

    /**
     * ????????? ????????? ?????? preference??? ????????? Doodle ????????? ????????? ?????? ????????????
     */
    private fun loadPreference() {
        val size = sharedPreferences.getInt(prefKeyBuilder("size"), DOODLE_SIZE_1)
        setDoodleSize(size)
        val color = sharedPreferences.getString(prefKeyBuilder("color"), color_ary[0])!!
        setDoodleColor(color)
    }

    /**
     * preference??? ????????? mosaic ????????? ????????? ?????? ????????????
     */
    private fun loadMosaicPreference() {
        // mosaic??? ????????? ?????? ??????
        val level = sharedPreferences.getInt(DOODLE_MOSAIC_LEVEL, DoodlePath.MOSAIC_LEVEL_1)
        btn_mosaic_level_1.isSelected = false
        btn_mosaic_level_2.isSelected = false
        btn_mosaic_level_3.isSelected = false
        when (level) {
            DOODLE_SIZE_1 -> btn_mosaic_level_1.isSelected = true
            DOODLE_SIZE_2 -> btn_mosaic_level_2.isSelected = true
            DOODLE_SIZE_3 -> btn_mosaic_level_3.isSelected = true
        }
        mDoodle.color = DoodlePath.getMosaicColor(mDoodle, level)

        // mosaic??? ????????? ?????? ??????
        val size = sharedPreferences.getInt(DOODLE_MOSAIC_SIZE, DEFAULT_MOSAIC_SIZE)
        more_mosaic_thickness_seekbar.progress = size
        setDoodleSize(size)
    }

    /**
     * DoodleView??? ???????????? ?????????????????? ??????????????? UI ??????
     */
    private inner class DoodleViewWrapper(context: Context?, bitmap: Bitmap?, optimizeDrawing: Boolean, listener: IDoodleListener?) : DoodleView(context, bitmap, optimizeDrawing, listener) {
        override fun setPen(pen: IDoodlePen) {
            super.setPen(pen)
            if (pen === DoodlePen.MOSAIC) {
                if (mMosaicLevel <= 0) {
                    mMosaicLevel = DoodlePath.MOSAIC_LEVEL_3
                    mDoodle.color = DoodlePath.getMosaicColor(mDoodle, mMosaicLevel)
                    if (mTouchGestureListener.selectedItem != null) {
                        // ????????? ???????????? ?????? item??? ????????? item??? ????????? ??????
                        mTouchGestureListener.selectedItem.color = mDoodle.getColor().copy()
                    }
                } else {
                    mDoodle.color = DoodlePath.getMosaicColor(mDoodle, mMosaicLevel)
                }
            }
        }
        // ??????????????? ????????? doodle ????????? ???????????? ID??? ?????????
        private val mBtnShapeIds: MutableMap<IDoodleShape, Int> = HashMap()

        /**
         * Doodle??? ????????? ????????? ???????????? (e.g. ??????, ?????????, ???, etc)
         */
        override fun setShape(shape: IDoodleShape) {
            if (shape === DoodleShape.SHAPE_MOSAIC) {
                super.setShape(DoodleShape.PAINTBRUSH)
                mDoodle.pen = DoodlePen.MOSAIC
            } else {
                super.setShape(shape)
                mDoodle.pen = DoodlePen.BRUSH
                setSingleSelected(mBtnShapeIds.values, mBtnShapeIds[shape]!!)
            }
        }

        /**
         * Doodle??? ????????? ????????? ???????????? UI ??????
         */
        override fun setSize(paintSize: Float) {
            super.setSize(paintSize)
            if (pen === DoodlePen.MOSAIC) {
                // mosaic???????????? seekbar??? ????????????
                val seekBar = more_mosaic_thickness_seekbar as MySeekBar
                seekBar.progress = paintSize.toInt()
            } else {
                // ????????? ???????????? ??????????????? ?????? ?????????
                var selection = 1
                if (mDoodle.shape == DoodleShape.ARROW) {
                    // ?????????????????? ??? ????????? ????????? ??? ?????????
                    if (paintSize == calcDoodlePaintSize(DOODLE_SIZE_1.toFloat()) * 2f) selection = 1
                    else if (paintSize == calcDoodlePaintSize(DOODLE_SIZE_2.toFloat()) * 1.5f) selection = 2
                    else if (paintSize == calcDoodlePaintSize(DOODLE_SIZE_3.toFloat()) * 1.5f) selection = 3
                } else {
                    if (paintSize == calcDoodlePaintSize(DOODLE_SIZE_1.toFloat())) selection = 1
                    else if (paintSize == calcDoodlePaintSize(DOODLE_SIZE_2.toFloat())) selection = 2
                    else if (paintSize == calcDoodlePaintSize(DOODLE_SIZE_3.toFloat())) selection = 3
                }
                doodle_size_selection.post {
                    if (selection == 1) {
                        doodle_size_selection.background = ContextCompat.getDrawable(context, R.drawable.doodle_size_1)
                        currDoodleSizeSelection = DOODLE_SIZE_1
                    } else if (selection == 2) {
                        doodle_size_selection.background = ContextCompat.getDrawable(context, R.drawable.doodle_size_2)
                        currDoodleSizeSelection = DOODLE_SIZE_2
                    } else if (selection == 3) {
                        doodle_size_selection.background = ContextCompat.getDrawable(context, R.drawable.doodle_size_3)
                        currDoodleSizeSelection = DOODLE_SIZE_3
                    }
                }
            }
            if (mTouchGestureListener.selectedItem != null) {
                mTouchGestureListener.selectedItem.size = size
            }
        }

        /**
         * Doodle??? ????????? ???????????? UI ??????
         */
        override fun setColor(color: IDoodleColor) {
            val pen = pen
            super.setColor(color)
            var doodleColor: DoodleColor? = null
            if (color is DoodleColor) {
                doodleColor = color
            }
            if (doodleColor != null
                    && canChangeColor(pen)) {
                // doodle??? ??????????????? ???????????? ??????
                for (id in mBtnColorIds.values) {
                    if (id == mBtnColorIds.get(doodleColor.color)) {
                        this@EditActivity.findViewById<ImageView>(id).setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_check_vector_black))
                    } else {
                        this@EditActivity.findViewById<ImageView>(id).setImageDrawable(null)
                    }
                }
                // ????????? ????????? item??? ????????? item??? ????????? ??????????????????
                if (mTouchGestureListener.selectedItem != null) {
                    mTouchGestureListener.selectedItem.color = getColor().copy()
                }
            }
            // mosaic??? ????????? ????????????
            if (doodleColor != null && pen === DoodlePen.MOSAIC && doodleColor.level != mMosaicLevel) {
                when (doodleColor.level) {
                    DoodlePath.MOSAIC_LEVEL_1 -> btn_mosaic_level_1.performClick()
                    DoodlePath.MOSAIC_LEVEL_2 -> btn_mosaic_level_2.performClick()
                    DoodlePath.MOSAIC_LEVEL_3 -> btn_mosaic_level_3.performClick()
                }
            }
        }

        /**
         * Doodle??? ????????? ??????????????? ????????????
         */
        override fun enableZoomer(enable: Boolean) {
            super.enableZoomer(enable)
            if (enable) {
                Toast.makeText(applicationContext, "x" + mDoodleParams.mZoomerScale, Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * Doodle??? Undo ???????????? undo/redo ????????? ???????????? ??????
         */
        override fun undo() {
            mTouchGestureListener.selectedItem = null
            val res = super.undo()
            updateDoodleUndoRedo()
        }

        /**
         * Doodle??? ???????????? ???????????? undo/redo ????????? ???????????? ??????
         */
        override fun clear() {
            super.clear()
            mTouchGestureListener.selectedItem = null
            updateDoodleUndoRedo()
        }

        /**
         * Doodle??? ????????? item??? ???????????? undo/redo ????????? ???????????? ??????
         */
        override fun addItem(item: IDoodleItem) {
            super.addItem(item)
            updateDoodleUndoRedo()
        }

        var mLastIsDrawableOutside: Boolean? = null

        /**
         * ??????????????? ????????????
         */
        override fun setEditMode(editMode: Boolean) {
            if (editMode == isEditMode) {
                return
            }
            super.setEditMode(editMode)

            val editIcon = ContextCompat.getDrawable(context, R.drawable.baseline_open_with_24)!!
            if (editMode) {
                Toast.makeText(applicationContext, R.string.doodle_edit_mode, Toast.LENGTH_SHORT).show()
                // ????????? ?????? ??????
                mLastIsDrawableOutside = mDoodle.isDrawableOutside
                mDoodle.setIsDrawableOutside(true)

                // ??????????????? ????????????
                editIcon.colorFilter = PorterDuffColorFilter(getColor(R.color.text_selected_color), PorterDuff.Mode.SRC_IN)
                menu_edit!!.findItem(R.id.menu_doodle_edit).icon = editIcon
                doodle_bottom_bar.beGone()
            } else {
                // ????????? ?????? ??????
                if (mLastIsDrawableOutside != null) mDoodle.setIsDrawableOutside(mLastIsDrawableOutside!!)
                mTouchGestureListener.center()
                if (mTouchGestureListener.selectedItem == null) {
                    setPen(pen)
                }
                mTouchGestureListener.selectedItem = null

                // ??????????????? ????????????
                menu_edit!!.findItem(R.id.menu_doodle_edit).icon = editIcon

                // Undo/Redo ?????? ??????
                updateDoodleUndoRedo()
                doodle_bottom_bar.beVisible()
            }
        }

        /**
         * View?????? ???????????? ??????
         * @param ids ?????? View?????? ID Collection
         * @param selectedId ??????????????? View??? ID
         */
        private fun setSingleSelected(ids: Collection<Int>, selectedId: Int) {
            for (id in ids) {
                this@EditActivity.findViewById<View>(id).isSelected = id == selectedId
            }
        }

        init {
            // ???????????? HashMap??? ID??? ??????
            mBtnShapeIds[DoodleShape.PAINTBRUSH] = R.id.btn_paint_brush
            mBtnShapeIds[DoodleShape.ARROW] = R.id.btn_arrow
            mBtnShapeIds[DoodleShape.HOLLOW_CIRCLE] = R.id.btn_holl_circle
            mBtnShapeIds[DoodleShape.HOLLOW_RECT] = R.id.btn_holl_rect
        }
    }
}
