package com.kr.gallery.pro.activities

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.text.format.DateFormat
import android.transition.Transition
import android.transition.TransitionInflater
import android.transition.TransitionManager
import android.util.ArrayMap
import android.util.Log
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.app.SharedElementCallback
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.kr.commons.extensions.*
import com.kr.commons.helpers.*
import com.kr.commons.models.FileDirItem
import com.kr.gallery.pro.BuildConfig
import com.kr.gallery.pro.R
import com.kr.gallery.pro.adapters.MyPagerAdapter
import com.kr.gallery.pro.custom.CustomSnackBar
import com.kr.gallery.pro.custom.ViewPagerViewModelFactory
import com.kr.gallery.pro.dialogs.*
import com.kr.gallery.pro.extensions.*
import com.kr.gallery.pro.fragments.ViewPagerFragment
import com.kr.gallery.pro.helpers.*
import com.kr.gallery.pro.models.HiddenMedium
import com.kr.gallery.pro.models.Medium
import com.kr.gallery.pro.viewmodels.ViewPagerViewModel
import com.kr.gallery.pro.views.KrBottomSheet
import kotlinx.android.synthetic.main.activity_view_pager.*
import kotlinx.android.synthetic.main.bottom_sheet_behavior.*
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * ????????????????????? ????????? activity
 * ??????????????????????????? ????????? ???????????????, Cover page??? click ????????? ??? ????????? ????????????.
 */
class ViewPagerActivity : SimpleActivity(), ViewPagerFragment.FragmentListener, TransferMethodSelectionDialog.TransferDialogListener, DeleteFolderDialog.DeleteDialogListener, RenameFileDialog.NameSetListener {

    private val TAG = "ViewPagerActivity"

    //????????????????????? ??? activity??? ???????????? intent??? ???????????? ???????????????
    private var mDirectory = ""
    //??????????????? ????????????
    private var mPath = ""
    //??????????????? ??????
    private var mPos = 0

    //???????????????
    private var mDirectoryList: ArrayList<String>? = null

    // ????????????????????? ???????????? ????????? ??????
    private var mTimeStamp = ""

    //?????????????????? ????????? ???????????? true??? ????????????.
    private var mShowHidden = false
    //????????????????????? ?????????????????? ???????????? ????????? ??????????????? ???????????????
    private var mTempHiddenMedium: HiddenMedium? = null

    //Slide????????? ???????????? true??? ????????????.
    private var mIsSlideshowActive = false
    //Slide????????? ?????? ?????????
    private var mSlideshowHandler = Handler()
    private var mSlideshowInterval = SLIDESHOW_DEFAULT_INTERVAL
    private var mSlideshowMoveBackwards = false
    private var mSlideshowMedia = ArrayList<Medium>()
    private var mAreSlideShowMediaVisible = false

    //????????????
    private var mMediaFiles = ArrayList<Medium>()

    //?????? ????????????????
    private var mIsFullScreen = true

    //????????? ??????/????????? ?????? handler, runnable, delay
    private var mTimeOutHandler = Handler()
    private lateinit var mTimeOutRunnable: Runnable
    private var mTimeOutDelay = 5000L

    //Bottom sheet?????? ????????? ????????? bottom sheet??????
    lateinit var bottomSheetBehavior: BottomSheetBehavior<KrBottomSheet>
    private var mSavedBottomState: Int = BottomSheetBehavior.STATE_COLLAPSED

    /**
     * Activity????????? ????????? dialog ???????????????
     * [TRANSFER_DIALOG], [DELETE_DIALOG], [RENAME_DIALOG]
     */
    private var dialogOpened = false
    private var dialogType = -1

    //?????? ????????? ?????? media??? ???????????? ???????????? ?????????
    private var originMedium: Medium? = null
    private var originFilePath = ""
    private var tempFilePath = ""
    private var currentSnackBar: CustomSnackBar? = null

    //Viewpager?????? ????????? PageTransformer
    private var pagerTransformer: ViewPager2.PageTransformer ?= null
    //Viewpager?????? ???????????? adapter
    private lateinit var mAdapter: MyPagerAdapter
    private var mPageChangeCallback = object: ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            // set transform scale to left & right
            val mScale = getPagerTransformerScale()

            val leftID = mAdapter.getItemId(position - 1)
            val currentID = mAdapter.getItemId(position)
            val rightID = mAdapter.getItemId(position + 1)

            val leftFrag = supportFragmentManager.findFragmentByTag("f$leftID") as ViewPagerFragment?
            leftFrag?.updateStateZoom(mScale)
            leftFrag?.setIsCurrentFrag(false)
            val centerFrag = supportFragmentManager.findFragmentByTag("f$currentID") as ViewPagerFragment?
            centerFrag?.updateStateZoom(mScale)
            centerFrag?.setIsCurrentFrag(true)
            val rightFrag = supportFragmentManager.findFragmentByTag("f$rightID") as ViewPagerFragment?
            rightFrag?.updateStateZoom(mScale)
            rightFrag?.setIsCurrentFrag(false)

            if (mPos != position) {
                mPos = position
                mPath = mMediaFiles[mPos].path
                updateActionbarTitle()
                invalidateOptionsMenu()
                scheduleSwipe()

                //????????? ????????? expand????????? collapsed????????? ????????????.
                if(bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED)
                {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    bottomSheet.expand(false)
                }
            }
        }
    }

    /* Activity transition??? ?????? ????????? */
    private var mStartTransitionKey = ""
    private var mMediumPathHashList: ArrayList<String>? = null
    private var mPrevHashcode = 0

    // ?????? item?????? ???????????? item??? ?????????????????? true else false
    var addForwardWhenUndo = true
    // item??? ?????? & ?????????????????? ????????????????????? ????????????.
    var deleteBySwipeUp = false
    private var mIsReturning = false
    private val mSharedElementCallback: SharedElementCallback = object : SharedElementCallback() {
        override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
            if (mIsReturning) {
                val currentID = mAdapter.getItemId(view_pager.currentItem)
                val currentFrag = supportFragmentManager.findFragmentByTag("f$currentID") as ViewPagerFragment

                val sharedElement: ImageView? = currentFrag.getGesturesView()
                if (sharedElement == null) {
                    // If shared element is null, then it has been scrolled off screen and
                    // no longer visible. In this case we cancel the shared element transition by
                    // removing the shared element from the shared elements map.
                    names.clear()
                    sharedElements.clear()
                } else if (mStartTransitionKey != sharedElement.transitionName) {
                    // If the user has swiped to a different ViewPager page, then we need to
                    // remove the old shared element and replace it with the new shared element
                    // that should be transitioned instead.
                    names.clear()
                    names.add(sharedElement.transitionName)
                    sharedElements.clear()
                    sharedElements[sharedElement.transitionName] = sharedElement
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {

        // ???????????? onCreate ?????? ??????????????? ??????.
        window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
        val transition = TransitionInflater.from(this).inflateTransition(R.transition.change_image_transform);
        transition.duration = 300

        // set an exit transition
        window.sharedElementEnterTransition = transition
        window.sharedElementExitTransition = transition

        super.onCreate(savedInstanceState)

        if (MyDebug.LOG) Log.d(TAG, "onCreate");

        //????????? ??????
        if (savedInstanceState != null) {
            mIsFullScreen = savedInstanceState.getBoolean(IS_FULLSCREEN)
            mSavedBottomState = savedInstanceState.getInt(LAST_BOTTOM_BAR_STATE)
            dialogOpened = savedInstanceState.getBoolean(DIALOG_OPENED, false)
            dialogType = savedInstanceState.getInt(DIALOG_TYPE)
        }

        setContentView(R.layout.activity_view_pager)

        postponeEnterTransition()
        setEnterSharedElementCallback(mSharedElementCallback)

        top_shadow.layoutParams.height = statusBarHeight + actionBarHeight
        checkNotchSupport()

        //?????????(?????? ?????? ?????????)??? media??????
        if(intent.hasExtra(DIRECTORY_LIST)){
            mDirectoryList = intent.extras?.get(DIRECTORY_LIST) as ArrayList<String>
            mShowHidden = intent.getBooleanExtra(SHOW_HIDDEN, false)
        }

        handlePermission(PERMISSION_WRITE_STORAGE) { it ->
            if (it) {
                initViewPager()

                mAdapter = MyPagerAdapter(this, mStartTransitionKey)
                if (!isDestroyed) {
                    mAdapter.shouldInitFragment = mPos < 5
                    view_pager.apply {
                        adapter = mAdapter
                        mAdapter.shouldInitFragment = true
                        registerOnPageChangeCallback(mPageChangeCallback)
                    }
                }

                //view model, observer ??????
                val viewModel: ViewPagerViewModel by viewModels { ViewPagerViewModelFactory(application, mDirectoryList, mShowHidden) }
                viewModel.mediaToShow.observe(this, androidx.lifecycle.Observer {
                    updateViewPager(it as ArrayList<Medium>)
                })
            } else {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        buildSheet()
        bottomSheetBehavior.state = mSavedBottomState
        bottomSheet.expand(mSavedBottomState == BottomSheetBehavior.STATE_EXPANDED)
        bottomSheetBehavior.disableDragging(mSavedBottomState)

        mTimeOutRunnable = Runnable {
            hideSystemUI(true)
        }

        checkSystemUI()

        //Dialog fragment??? listener??? ???????????????
        if(dialogOpened) {
            when(dialogType) {
                TRANSFER_DIALOG -> {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    val dialog = supportFragmentManager.findFragmentByTag(MainActivity.TRANSFER_DIALOG_TAG) as TransferMethodSelectionDialog
                    dialog.addListener(this)
                }
                DELETE_DIALOG -> {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    val dialog = supportFragmentManager.findFragmentByTag(DELETE_DIALOG_TAG) as DeleteFolderDialog
                    dialog.addListener(this)
                }
                RENAME_DIALOG -> {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    val dialog = supportFragmentManager.findFragmentByTag(MainActivity.RENAME_DIALOG_TAG) as RenameFileDialog
                    dialog.addListener(this)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()

        //Permission??????
        if (!hasPermission(PERMISSION_WRITE_STORAGE)) {
            finish()
            return
        }

        window.navigationBarColor = Color.TRANSPARENT

        setupOrientation()

        if (!mIsFullScreen) restartTimeOut()

        invalidateOptionsMenu()

        Handler().postDelayed(Runnable {
            // slideshow view
            updateViewPagerTransformer()
        }, 1000)
    }

    override fun finishAfterTransition() {
        mIsReturning = true

        val currentID = mAdapter.getItemId(view_pager.currentItem)
        val currentFrag = supportFragmentManager.findFragmentByTag("f$currentID") as ViewPagerFragment

        val data = Intent()
        data.putExtra(EXTRA_CURRENT_TRANSITION_KEY, currentFrag.getGesturesView()!!.transitionName)

        // ?????????????????? ??????????????? mTimeStamp??? ???????????????
        if (mTimeStamp == "") {
            data.putExtra(EXTRA_POSITION_ON_VIEWPAGER, view_pager.currentItem)
            setResult(Activity.RESULT_OK, data)

            super.finishAfterTransition()
        }
        else {
            val currentPos = view_pager.currentItem
            val curMedium = mAdapter.getMedium(currentPos)
            val fileDate = curMedium.date
            val fileType = curMedium.type

            ensureBackgroundThread {
                val newMediums = mediaDB.getMediaListByDate(fileDate, fileType) as ArrayList<Medium>
                for (i in 0 until newMediums.size) {
                    val medium = newMediums[i]
                    if (medium.name == curMedium.name && medium.modified == curMedium.modified) {
                        runOnUiThread {
                            data.putExtra(EXTRA_POSITION_ON_VIEWPAGER, i)
                            setResult(Activity.RESULT_OK, data)

                            super.finishAfterTransition()
                        }
                        break
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        //Snackbar??? ???????????? ??????
        if(currentSnackBar != null) {
            currentSnackBar!!.closeSnackBar(true, ignoreFinishCallback = false)
            currentSnackBar = null

            if (mAdapter.itemCount == 0) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }

            return
        }

        //Bottom sheet?????? ??????
        if(bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            //Collapse the bottom sheet
            mSavedBottomState = BottomSheetBehavior.STATE_COLLAPSED
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            bottomSheet.toggleExpand()
            return
        }

        // null??? ???????????? ????????? exit transition??? ???????????? ??????????????? ?????? ????????????.
        view_pager.setPageTransformer(null)

        // ??????????????? MediaActivity??? thumbnail??? ????????? ??????
        // ????????? ????????? ????????? Transition ??????
        val currentID = mAdapter.getItemId(view_pager.currentItem)
        val currentFrag = supportFragmentManager.findFragmentByTag("f$currentID") as ViewPagerFragment
        val currentTransitionKey = currentFrag.getGesturesView()!!.transitionName

        var findKey = false;
        if (mMediumPathHashList != null) {
            for (transitionKey in this.mMediumPathHashList!!) {
                if (transitionKey.equals(currentTransitionKey)) {
                    findKey = true
                    break;
                }
            }
        }

        // ?????????????????? ??????????????? mTimeStamp??? ???????????????
        val today = formatDate(System.currentTimeMillis())

        val currentPos = view_pager.currentItem
        val curMedium = mAdapter.getMedium(currentPos)
        val fileDate = curMedium.date;

        if (findKey || (mTimeStamp == today && fileDate == today) || mTimeStamp == "") {
            supportFinishAfterTransition()
        } else {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    /**
     * @param timeMillis: ???????????? ??? ????????????
     * @return ????????? ????????????(2021-1-1)
     * @see DATE_FORMAT_ONE
     */
    private fun formatDate(timeMillis: Long): String {
        val cal = Calendar.getInstance(Locale.ENGLISH)
        cal.timeInMillis = timeMillis
        return DateFormat.format(DATE_FORMAT_ONE, cal).toString()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        //????????? ??????
        outState.putBoolean(IS_FULLSCREEN, mIsFullScreen)
        outState.putInt(LAST_BOTTOM_BAR_STATE, bottomSheetBehavior.state)
        outState.putBoolean(DIALOG_OPENED, dialogOpened)
        outState.putInt(DIALOG_TYPE, dialogType)
    }

    /**
     * ????????? ?????? [mTimeOutDelay] ??? ????????? ?????????????????? ????????????.
     */
    private fun restartTimeOut() {
        stopTimeOut()
        mTimeOutHandler.postDelayed(mTimeOutRunnable, mTimeOutDelay)
    }

    /**
     * Leak??? ????????? ?????? handler?????? callback??? ?????????.
     */
    private fun stopTimeOut() {
        mTimeOutHandler.removeCallbacksAndMessages(null)
    }

    override fun onPause() {
        super.onPause()

        stopTimeOut()
        stopSlideshow()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimeOut()
        view_pager.unregisterOnPageChangeCallback(mPageChangeCallback)

        removeActivityFromTransitionManager()
    }

    private fun removeActivityFromTransitionManager() {
        val transitionManagerClass: Class<*> = TransitionManager::class.java
        try {
            val runningTransitionsField: Field =
                    transitionManagerClass.getDeclaredField("sRunningTransitions")
            runningTransitionsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val runningTransitions: ThreadLocal<WeakReference<ArrayMap<ViewGroup, ArrayList<Transition>>>?> =
                    runningTransitionsField.get(transitionManagerClass) as ThreadLocal<WeakReference<ArrayMap<ViewGroup, ArrayList<Transition>>>?>
            if (runningTransitions.get() == null || runningTransitions.get()?.get() == null) {
                return
            }
            val map: ArrayMap<ViewGroup, ArrayList<Transition>> =
                    runningTransitions.get()?.get() as ArrayMap<ViewGroup, ArrayList<Transition>>
            map[window.decorView]?.let { transitionList ->
                transitionList.forEach { transition ->
                    //Add a listener to all transitions. The last one to finish will remove the decor view:
                    transition.addListener(object : Transition.TransitionListener {
                        override fun onTransitionEnd(transition: Transition) {
                            //When a transition is finished, it gets removed from the transition list
                            // internally right before this callback. Remove the decor view only when
                            // all the transitions related to it are done:
                            if (transitionList.isEmpty()) {
                                map.remove(window.decorView)
                            }
                            transition.removeListener(this)
                        }

                        override fun onTransitionCancel(transition: Transition?) {}
                        override fun onTransitionPause(transition: Transition?) {}
                        override fun onTransitionResume(transition: Transition?) {}
                        override fun onTransitionStart(transition: Transition?) {}
                    })
                }
                //If there are no active transitions, just remove the decor view immediately:
                if (transitionList.isEmpty()) {
                    map.remove(window.decorView)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun buildSheet() {
        //Bottom bar??? ?????? view??? ??????
        bottomSheet.addItem(
                applicationContext,
                KEY_SHARE,
                R.drawable.ic_outline_share_24,
                R.string.share,
                false
        )
        bottomSheet.addItem(
                applicationContext,
                KEY_DELETE,
                R.drawable.ic_outline_delete_24,
                R.string.delete,
                false
        )
        bottomSheet.addItem(
                applicationContext,
                KEY_EDIT,
                R.drawable.ic_edit_vector,
                R.string.edit,
                false
        )
        bottomSheet.addItem(
                applicationContext,
                KEY_EXPAND,
                R.drawable.ic_outline_more_horiz_24,
                R.string.expand,
                false
        )

        bottomSheet.addItem(applicationContext, KEY_ENCRYPT, 0, R.string.encrypt, true)
        bottomSheet.addItem(applicationContext, KEY_AUTO_SHOW, 0, R.string.auto_show, true)
        bottomSheet.addItem(applicationContext, KEY_RENAME, 0, R.string.rename, true)
        bottomSheet.addItem(applicationContext, KEY_SET_PICTURE, 0, R.string.picture_set, true)
        bottomSheet.addItem(applicationContext, KEY_MOVE, 0, R.string.move, true)

        //Bottom bar???????????? ????????????
        bottomSheet.itemMap[KEY_EXPAND]!!.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                mSavedBottomState = BottomSheetBehavior.STATE_EXPANDED
            }
            else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                mSavedBottomState = BottomSheetBehavior.STATE_COLLAPSED
            }

            bottomSheet.toggleExpand()
        }

        bottomSheet.itemMap[KEY_SHARE]!!.setOnClickListener {
            stopTimeOut()
            onClickShare()
        }
        bottomSheet.itemMap[KEY_DELETE]!!.setOnClickListener {
            stopTimeOut()
            onClickDelete()
        }
        bottomSheet.itemMap[KEY_EDIT]!!.setOnClickListener {
            stopTimeOut()
            onClickEdit()
        }
        bottomSheet.itemMap[KEY_ENCRYPT]!!.setOnClickListener {
            stopTimeOut()

            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            bottomSheet.expand(false)
        }
        bottomSheet.itemMap[KEY_AUTO_SHOW]!!.setOnClickListener {
            onClickAutoShow()
        }
        bottomSheet.itemMap[KEY_RENAME]!!.setOnClickListener {
            stopTimeOut()
            onClickRename()
        }
        bottomSheet.itemMap[KEY_SET_PICTURE]!!.setOnClickListener {
            stopTimeOut()
            setAs(getCurrentPath())

            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            bottomSheet.expand(false)
        }
        bottomSheet.itemMap[KEY_MOVE]!!.setOnClickListener {
            stopTimeOut()
            moveFileTo()

            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            bottomSheet.expand(false)
        }
    }

    //??????????????? ????????????
    private fun onClickShare(){
        if(dialogOpened)
            return

        //Bottom bar??? ?????????
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheet.expand(false)

        //?????????????????? ??????
        val dialog = TransferMethodSelectionDialog(this)
        dialog.show(supportFragmentManager, MainActivity.TRANSFER_DIALOG_TAG)

        //????????????
        dialogOpened = true
        dialogType = TRANSFER_DIALOG
    }

    //??????-?????????????????? ????????????
    override fun onMessage() {
        dialogOpened = false

        runAfterDelay {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            shareMediumPath(getCurrentPath(), SHARE_BY_MESSAGE)
            restartTimeOut()
        }
    }

    //??????-?????????????????? ????????????
    override fun onNote() {
        dialogOpened = false

        runAfterDelay {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            shareMediumPath(getCurrentPath(), SHARE_BY_NOTE)
            restartTimeOut()
        }
    }

    //??????-????????????????????? ????????????
    override fun onBluetooth() {
        dialogOpened = false

        runAfterDelay {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            shareMediumPath(getCurrentPath(), SHARE_BY_BLUETOOTH)
            restartTimeOut()
        }
    }

    //??????????????? ?????? ????????????????????? cancel??????
    override fun onCancel() {
        dialogOpened = false

        runAfterDelay {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    //??????????????? ????????????
    private fun onClickDelete(){
        if(dialogOpened)
            return

        //Bottom bar??? ?????????
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheet.expand(false)

        //?????????????????? ??????
        val dialog = DeleteFolderDialog(true, true, this)
        dialog.show(supportFragmentManager, DELETE_DIALOG_TAG)

        //????????????
        dialogOpened = true
        dialogType = DELETE_DIALOG
    }

    //???????????????-????????? ????????????
    override fun onDelete() {
        val path = getCurrentPath()

        handleSAFDialog(arrayListOf(path)){
            if(it) {
                deleteBySwipeUp = false
                //?????? ??????, ?????? ??????
                val currentPos = view_pager.currentItem
                if (currentPos == mAdapter.itemCount - 1)
                    mPos = currentPos - 1
                if (mPos < 0)
                    mPos = 0

                if (mAdapter.itemCount > 1) {
                    mPath = mMediaFiles[mPos].path
                }
                runAfterDelay {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    ensureBackgroundThread {
                        if (mShowHidden) {
                            if (deleteFileWithPath(path, false))
                                hiddenMediumDao.deleteItem(path)
                        } else {
                            deleteFileWithPath(path, true)
                        }
                    }
                    restartTimeOut()
                }
                dialogOpened = false
            }
            else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                restartTimeOut()
                dialogOpened = false
                toast(R.string.one_file_delete_failed, Toast.LENGTH_LONG)
            }
        }
    }

    //????????????????????? ????????????
    private fun onClickRename(){
        if(dialogOpened)
            return

        //Bottom bar??? ?????????
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheet.expand(false)

        //???????????????????????? ?????????
        val fileName = getCurrentPath().getFilenameFromPath().getFilenameContent()
        val dialog = RenameFileDialog(fileName, this)
        dialog.show(supportFragmentManager, MainActivity.RENAME_DIALOG_TAG)

        //????????????
        dialogOpened = true
        dialogType = RENAME_DIALOG
    }

    //?????????????????????-??????????????? ????????????
    override fun onNameSet(name: String) {
        val filePath = getCurrentPath()

        runAfterDelay {
            handleSAFDialog(arrayListOf(filePath)){
                if(it) {
                    //bottom bar ??? ?????? ????????????
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    val folderPath = filePath.getParentPath()
                    val fileExtension = filePath.getFilenameExtension()
                    val newFileName = "$name.$fileExtension"
                    mPath = "$folderPath/$newFileName"

                    val oldFile = File(filePath)
                    val newFile = File(folderPath, newFileName)
                    renameFile(oldFile, newFile)
                }
                else {
                    //bottom bar ??? ?????? ????????????
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    toast(R.string.file_rename_failed, Toast.LENGTH_LONG)
                }
            }
        }
        dialogOpened = false
    }

    //????????????????????? ????????????
    private fun onClickAutoShow(){
        initSlideshow()

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheet.expand(false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_viewpager, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (getCurrentMedium() == null)
            return true

        when (item.itemId) {
            R.id.menu_properties -> showProperties()
            android.R.id.home -> onBackPressed()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == REQUEST_EDIT_IMAGE && resultCode == Activity.RESULT_OK && resultData != null) {
            mPos = -1
            mPrevHashcode = 0

            val medium = resultData.getSerializableExtra(RESULT_MEDIUM) as Medium
            imageSavedAfterEditing(medium)
        } else if (requestCode == REQUEST_SET_AS && resultCode == Activity.RESULT_OK) {
            toast(R.string.wallpaper_set_successfully)
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun initViewPager() {
        val uri = intent.data
        if (uri != null) {
            var cursor: Cursor? = null
            try {
                val proj = arrayOf(Images.Media.DATA)
                cursor = contentResolver.query(uri, proj, null, null, null)
                if (cursor?.moveToFirst() == true) {
                    mPath = cursor.getStringValue(Images.Media.DATA)
                }
            } finally {
                cursor?.close()
            }
        } else {
            try {
                mPath = intent.getStringExtra(PATH)
            } catch (e: Exception) {
                showErrorToast(e)
                finish()
                return
            }
        }

        if (intent.extras?.containsKey(REAL_FILE_PATH) == true) {
            mPath = intent.extras!!.getString(REAL_FILE_PATH)!!
        }

        if (mPath.isEmpty()) {
            toast(R.string.unknown_error_occurred)
            finish()
            return
        }

        mStartTransitionKey = if (intent.hasExtra(TRANSITION_KEY)) intent.getStringExtra(TRANSITION_KEY)!! else ""
        mMediumPathHashList = if (intent.hasExtra(MEDIUM_PATH_HASH_LIST)) intent.extras!!.get(MEDIUM_PATH_HASH_LIST) as ArrayList<String> else null
        mTimeStamp = if(intent.hasExtra(TIMESTAMP)) intent.getStringExtra(TIMESTAMP)!! else ""

        if (!getDoesFilePathExist(mPath) && getPortraitPath() == "") {
            finish()
            return
        }

        hideSystemUI(true)
        initContinue()
    }

    private fun initContinue() {
        mDirectory = mPath.getParentPath()
        supportActionBar?.title = mPath.getFilenameFromPath()

        //view_pager??????
        view_pager.offscreenPageLimit = 2

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (mAdapter.itemCount > 0) {
                mIsFullScreen = if (visibility and View.SYSTEM_UI_FLAG_LOW_PROFILE == 0) {
                    false
                } else {
                    visibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
                }

                checkSystemUI()
                fullscreenToggled()
            }
        }
    }

    private fun setupOrientation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    //Adapter??? ????????? ????????????.
    private fun updatePagerItems(media: ArrayList<Medium>) {
        if (!isDestroyed) {
            mAdapter.setItems(media)
            // ???????????? ??????&??????????????? ????????????&????????? 0???????????? ???????????? 1????????? ????????? ????????? ??????????????? ????????? ?????? ?????????.
            if (mPos == 0) {
                if (view_pager.adapter!!.itemCount > 1 ) {
                    // adapter??? ?????????????????? ?????????????????? ???????????? ????????? ?????? ?????????.
                    view_pager.setCurrentItem(1, false)
                    view_pager.setCurrentItem(0, false)
                } else view_pager.adapter = mAdapter
            } else {
                view_pager.setCurrentItem(mPos, false)
            }

            updateActionbarTitle()
        }
    }

    private fun updateViewPagerTransformer() {
        pagerTransformer = CustPagerTransformer(applicationContext, view_pager, supportFragmentManager)
        view_pager.setPageTransformer(pagerTransformer)
    }

    fun setPagerTransformerScale(scale: Float) {
        if (pagerTransformer != null) {
            (pagerTransformer as CustPagerTransformer).setScale(scale)
            view_pager.requestTransform()
        }
    }

    fun getViewPager(): ViewPager2 {
        return view_pager
    }

    var lastViewForListen : View? = null
    fun runRemoveAnimation() {
        val currentPos = view_pager.currentItem

        // animate other views
        val transformer = pagerTransformer as CustPagerTransformer

        val leftBeforeFragContainer = transformer.leftBeforeFragContainer
        val leftFragContainer = transformer.leftFragContainer
        val rightFragContainer = transformer.rightFragContainer
        val rightNextFragContainer = transformer.rightNextFragContainer

        val scale = (pagerTransformer as CustPagerTransformer).getScale()
        val animDuration = 300L

        val listener = object : Animator.AnimatorListener{
            override fun onAnimationRepeat(p0: Animator?) {}
            override fun onAnimationEnd(p0: Animator?) {
                // this need, because it is called twice later
                lastViewForListen!!.animate().setListener(null)
                removeCurrentFragment()
            }
            override fun onAnimationCancel(p0: Animator?) {}
            override fun onAnimationStart(p0: Animator?) {}
        }
        if (currentPos < mAdapter.itemCount - 1) {
            if (rightFragContainer != null) {
                val width = rightFragContainer.measuredWidth
                val delta = width * (1 - scale) - dp2px(20f)
                rightFragContainer.animate().setListener(listener).translationXBy(-width + delta).duration = animDuration
                lastViewForListen = rightFragContainer
            }
            if (currentPos < mAdapter.itemCount - 2) {
                if (rightNextFragContainer != null) {
                    val width = rightNextFragContainer.measuredWidth
                    val delta = width * (1 - scale) - dp2px(20f)
                    rightNextFragContainer.animate().translationXBy(-width + delta).duration = animDuration
                }
            }
            addForwardWhenUndo = true
        } else {
            if (currentPos > 0) {
                if (leftFragContainer != null) {
                    val width = leftFragContainer.measuredWidth
                    val delta = width * (1 - scale) - dp2px(20f)
                    leftFragContainer.animate().setListener(listener).translationXBy(width - delta).duration = animDuration
                    lastViewForListen = leftFragContainer
                }
                if (currentPos > 1) {
                    if (leftBeforeFragContainer != null) {
                        val width = leftBeforeFragContainer.measuredWidth
                        val delta = width * (1 - scale) - dp2px(20f)
                        leftBeforeFragContainer.animate().translationXBy(width - delta).duration = animDuration
                    }
                }
            }
            addForwardWhenUndo = false
        }

        if (mAdapter.itemCount == 1) {
            if (!mIsFullScreen) fragmentClicked()
            removeCurrentFragment()
        }
    }

    fun runUndoAnimation(sPos: Int, sPath: String, sOriginFilePath: String, sTempFilePath: String) {
        val currentPos = view_pager.currentItem
        // animate other views
        val transformer = pagerTransformer as CustPagerTransformer

        val leftFragContainer = transformer.leftFragContainer
        val currFragContainer = transformer.currFragContainer
        val rightFragContainer = transformer.rightFragContainer

        val scale = (pagerTransformer as CustPagerTransformer).getScale()
        val animDuration = 300L

        val listener = object : Animator.AnimatorListener{
            override fun onAnimationRepeat(p0: Animator?) {}
            override fun onAnimationEnd(p0: Animator?) {
                lastViewForListen!!.animate().setListener(null)
                undoMedium(sPos, sPath, sOriginFilePath, sTempFilePath)
            }
            override fun onAnimationCancel(p0: Animator?) {}
            override fun onAnimationStart(p0: Animator?) {}
        }

        if (addForwardWhenUndo) {
            if (currentPos <= mAdapter.itemCount - 1) {
                if (currFragContainer != null) {
                    val width = currFragContainer.measuredWidth
                    val delta = width * (1 - scale) - dp2px(20f)
                    currFragContainer.animate().setListener(listener).translationXBy(width - delta).duration = animDuration
                    lastViewForListen = currFragContainer
                }
                if (currentPos < mAdapter.itemCount - 1) {
                    if (rightFragContainer != null) {
                        val width = rightFragContainer.measuredWidth
                        val delta = width * (1 - scale) - dp2px(20f)
                        rightFragContainer.animate().translationXBy(width - delta).duration = animDuration
                    }
                }
            }
        } else {
            if (currentPos == mAdapter.itemCount - 1) undoMedium(sPos, sPath, sOriginFilePath, sTempFilePath)
            else {
                if (currentPos >= 0) {
                    if (currFragContainer != null) {
                        val width = currFragContainer.measuredWidth
                        val delta = width * (1 - scale) - dp2px(20f)
                        currFragContainer.animate().setListener(listener).translationXBy(-width + delta).duration = animDuration
                        lastViewForListen = currFragContainer
                    }
                    if (currentPos > 0) {
                        if (leftFragContainer != null) {
                            val width = leftFragContainer.measuredWidth
                            val delta = width * (1 - scale) - dp2px(20f)
                            leftFragContainer.animate().translationXBy(-width + delta).duration = animDuration
                        }
                    }
                }
            }
        }

        if (mAdapter.itemCount == 0) undoMedium(sPos, sPath, sOriginFilePath, sTempFilePath)
    }

    /**
     * ????????? ?????? ???????????? ????????????.
     * ?????? ????????? animation??? ?????? ??????????????? ?????? ????????? ????????????.
     */
    fun removeCurrentFragment() {
        deleteBySwipeUp = true
        val currentPos = view_pager.currentItem
        originMedium = mAdapter.getMedium(currentPos)

        if(currentPos == mAdapter.itemCount - 1)
            mPos = currentPos - 1
        if(mPos < 0)
            mPos = 0
        mPath = mMediaFiles[mPos].path

        mMediaFiles.removeAt(currentPos)
        updatePagerItems(mMediaFiles)

        //??????????????? ?????? ?????? ?????????
        originFilePath = originMedium!!.path
        if(mShowHidden) {
            //?????????????????? ??????????????? ??????????????? ????????????
            mTempHiddenMedium = HiddenMedium(originMedium!!)
        }
        deleteMediumTemporarily()

        val sPos = mPos
        val sPath = mPath
        val sOriginFilePath = originFilePath
        val sTempFilePath = tempFilePath

        //??????????????? ?????? snack bar??? ??????
        if(currentSnackBar != null) {
            currentSnackBar!!.closeSnackBar(false)
            currentSnackBar = null
        }
        currentSnackBar = CustomSnackBar.show(this, fragment_holder, R.string.item_removed, R.string.undo, mAdapter.itemCount,
                {
                    //animation ??????
                    runUndoAnimation(sPos, sPath, sOriginFilePath, sTempFilePath)
                },
                {
                    deleteMediumCompletely(sTempFilePath)
                    if (currentSnackBar?.tag == 0) {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                    currentSnackBar = null
                }
        )

        //????????????
        var bottomMargin = Utils.convertDpToPixel(10f, this).toInt()
        if(bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomMargin += collapsedItemsContainer.height
        }
        else if(bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomMargin += (collapsedItemsContainer.height + expandedItemsContainer.height)
        }

        val lp = currentSnackBar!!.layoutParams as RelativeLayout.LayoutParams
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
        lp.bottomMargin = bottomMargin
        lp.width = MATCH_PARENT
        currentSnackBar!!.layoutParams = lp
    }

    private fun undoMedium(sPos: Int, sPath: String, sOriginFilePath: String, sTempFilePath: String) {
        // undo
        currentSnackBar = null
        mPos = sPos
        mPath = sPath
        undoDeleteMedium(sOriginFilePath, sTempFilePath)
    }

    /**
     * ??????????????? ???????????????.
     * ?????? ????????? ?????? ???????????? ??????????????? ?????????????????????.
     */
    private fun deleteMediumTemporarily() {
        val fileName = originFilePath.getFilenameFromPath()
        val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val newFileName = "." + fileName.getFilenameContent() + "_" + simpleDateFormat.format(Date(System.currentTimeMillis())) + "." + TEMP_DELETED_FILE_EXTENSION
        val folderPath = originFilePath.getParentPath()
        val newFilePath = "$folderPath/$newFileName"
        tempFilePath = newFilePath

        ensureBackgroundThread {
            val oldFile = File(originFilePath)
            val newFile = File(newFilePath)
            //?????? ????????? ???????????? ??????
            if(File(newFilePath).exists()) {
                newFile.delete()
            }

            if(oldFile.renameTo(newFile)) {
                if(mShowHidden) {
                    //Hidden media table?????? ?????? ??????
                    hiddenMediumDao.deleteItem(originFilePath)
                }
                else {
                    //Media store?????? ?????? ?????? ??????
                    val where = "${MediaStore.MediaColumns.DATA} = ?"
                    val args = arrayOf(originFilePath)
                    contentResolver.delete(getFileUri(originFilePath), where, args)

                    //Media table?????? ?????? ?????? ??????
                    mediaDB.deleteItem(originFilePath)
                }
            }
        }
    }

    /**
     * ?????? ???????????????
     * Snack bar??? ???????????? ???????????? ??????????????? ??????????????? ?????? ???????????? ??????????????????.
     * ??? ????????? ?????????.
     * @param _tempFilePath: ??????????????? ??????????????? ??? ????????????
     * @see deleteMediumTemporarily
     * @see undoDeleteMedium
     */
    private fun deleteMediumCompletely(_tempFilePath: String) {
        ensureBackgroundThread {
            val file = File(_tempFilePath)
            if(file.exists()) {
                file.delete()
            }
        }
    }

    /**
     * ??????????????? ??????
     * ?????? ????????? ??????????????? ???????????? ????????????????????????.
     * @param _originFilePath ????????? ????????????
     * @param _tempFilePath ??????????????? ??????????????? ??? ????????????
     * @see deleteMediumTemporarily
     * @see deleteMediumCompletely
     */
    private fun undoDeleteMedium(_originFilePath: String, _tempFilePath: String) {
        ensureBackgroundThread {
            val originFile = File(_originFilePath)
            val currentFile = File(_tempFilePath)
            if (currentFile.exists()) {
                if (currentFile.renameTo(originFile)) {
                    mPath = _tempFilePath

                    if(mShowHidden){
                        //Hidden media table??? ?????? ??????
                        if(mTempHiddenMedium != null) {
                            hiddenMediumDao.insert(mTempHiddenMedium!!)
                        }
                    }
                    else {
                        //Media store??? ??? ?????? ??????
                        MediaScannerConnection.scanFile(applicationContext, arrayOf(originFile.toString()),
                                arrayOf(originFile.name)) { _, _ -> }

                        //Media table??? ??????
                        mediaDB.insertItem(originMedium!!)
                    }
                }
            }
        }
    }

    fun getPagerTransformerScale() : Float {
        return if (pagerTransformer == null) 1f
        else (pagerTransformer as CustPagerTransformer).getScale()
    }

    /**
     * Slide show???????????? ????????? ???????????? ??????
     * ???????????? ????????? slide show??? ????????????.
     * @see startSlideshow
     */
    private fun initSlideshow() {
        SlideshowDialog(this) {
            startSlideshow()
        }
    }

    /**
     * Slide show ????????????
     */
    private fun startSlideshow() {
        if (getMediaForSlideshow()) {
            view_pager.onGlobalLayout {
                if (!isDestroyed) {
                    if (config.slideshowAnimation == SLIDESHOW_ANIMATION_FADE) {
                        view_pager.setPageTransformer(FadePageTransformer())
                    }

                    hideSystemUI(true)
                    mSlideshowInterval = config.slideshowInterval
                    mSlideshowMoveBackwards = config.slideshowMoveBackwards
                    mIsSlideshowActive = true
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    scheduleSwipe()
                }
            }
        }
    }

    private fun goToNextMedium(forward: Boolean) {
        val oldPosition = view_pager.currentItem
        val newPosition = if (forward) oldPosition + 1 else oldPosition - 1
        if (newPosition == -1 || newPosition > mAdapter.itemCount - 1) {
            slideshowEnded(forward)
        } else {
            view_pager.setCurrentItem(newPosition, false)
        }
    }

    private fun animatePagerTransition(forward: Boolean) {
        val oldPosition = view_pager.currentItem
        val animator = ValueAnimator.ofInt(0, view_pager.width)
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) {
            }

            override fun onAnimationEnd(animation: Animator?) {
                if (view_pager.isFakeDragging) {
                    try {
                        view_pager.endFakeDrag()
                    } catch (ignored: Exception) {
                        stopSlideshow()
                    }

                    if (view_pager.currentItem == oldPosition) {
                        slideshowEnded(forward)
                    }
                }
            }

            override fun onAnimationCancel(animation: Animator?) {
                view_pager.endFakeDrag()
            }

            override fun onAnimationStart(animation: Animator?) {
            }
        })

        if (config.slideshowAnimation == SLIDESHOW_ANIMATION_SLIDE) {
            animator.interpolator = DecelerateInterpolator()
            animator.duration = SLIDESHOW_SLIDE_DURATION
        } else {
            animator.duration = SLIDESHOW_FADE_DURATION
        }

        animator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            var oldDragPosition = 0
            override fun onAnimationUpdate(animation: ValueAnimator) {
                if (view_pager?.isFakeDragging == true) {
                    val dragPosition = animation.animatedValue as Int
                    val dragOffset = dragPosition - oldDragPosition
                    oldDragPosition = dragPosition
                    try {
                        view_pager.fakeDragBy(dragOffset * (if (forward) -1f else 1f))
                    } catch (e: Exception) {
                        stopSlideshow()
                    }
                }
            }
        })

        view_pager.beginFakeDrag()
        animator.start()
    }

    /**
     * slide show??? ???????????? ????????? ???????????? ??????????????? ????????????.
     * @param forward ????????????, true: ?????????, false: ??????
     */
    private fun slideshowEnded(forward: Boolean) {
        if (config.loopSlideshow) {
            if (forward) {
                view_pager.setCurrentItem(0, false)
            } else {
                view_pager.setCurrentItem(mAdapter.itemCount - 1, false)
            }
        } else {
            stopSlideshow()
            toast(R.string.slideshow_ended)
        }
    }

    /**
     * Slide show??????
     */
    private fun stopSlideshow() {
        if (mIsSlideshowActive) {
            view_pager.setPageTransformer(DefaultPageTransformer())
            mIsSlideshowActive = false
            showSystemUI(true)
            mSlideshowHandler.removeCallbacksAndMessages(null)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun scheduleSwipe() {
        mSlideshowHandler.removeCallbacksAndMessages(null)
        if (mIsSlideshowActive) {
            if (getCurrentMedium()!!.isImage()) {
                mSlideshowHandler.postDelayed({
                    if (mIsSlideshowActive && !isDestroyed) {
                        swipeToNextMedium()
                    }
                }, mSlideshowInterval * 1000L)
            } else {
//                (getCurrentFragment() as? VideoFragment1)!!.playVideo()
            }
        }
    }

    private fun swipeToNextMedium() {
        if (config.slideshowAnimation == SLIDESHOW_ANIMATION_NONE) {
            goToNextMedium(!mSlideshowMoveBackwards)
        } else {
            animatePagerTransition(!mSlideshowMoveBackwards)
        }
    }

    private fun getMediaForSlideshow(): Boolean {
        mSlideshowMedia = mMediaFiles.filter {
            it.isImage() || (config.slideshowIncludeVideos && it.isVideo())
        } as ArrayList<Medium>

        if (config.slideshowRandomOrder) {
            mSlideshowMedia.shuffle()
            mPos = 0
        } else {
            mPath = getCurrentPath()
            mPos = getPositionInList(mSlideshowMedia)
            if(mPos == -1) mPos = 0
        }

        return if (mSlideshowMedia.isEmpty()) {
            toast(R.string.no_media_for_slideshow)
            false
        } else {
            updatePagerItems(mSlideshowMedia)
            mAreSlideShowMediaVisible = true
            true
        }
    }

    private fun moveFileTo() {
        val currPath = getCurrentPath()

        //????????? ????????????
        val oldPos = mPos
        if(mPos == mMediaFiles.size - 1)
            mPos = 0

        val fileDirItems = arrayListOf(FileDirItem(currPath, currPath.getFilenameFromPath()))
        moveFilesTo(fileDirItems) {
            success, _ ->
            if(!success) {
                toast(R.string.moving_failed)
                mPos = oldPos
            }
            else {
                toast(R.string.moving_success)
                mIsFullScreen = false
                fullscreenToggled()
            }
        }
    }

    private fun getPortraitPath() = intent.getStringExtra(PORTRAIT_PATH) ?: ""

    fun showProperties() {
        if (getCurrentMedium() != null &&
                supportFragmentManager.findFragmentByTag(PROPERTY_DIALOG_TAG) == null) {
            val propertiesDialogFragment: DialogFragment? = PropertiesDialogFragment.newInstance(getCurrentPath(), false)
            propertiesDialogFragment?.show(supportFragmentManager, PROPERTY_DIALOG_TAG)
        }
    }

    //??????????????? ????????????
    private fun onClickEdit(){
        val path = getCurrentPath()
        val newPath = path.removePrefix("file://")
        openEditor(newPath, BuildConfig.APPLICATION_ID)
    }

    private fun openEditor(path: String, applicationId: String) {
        ensureBackgroundThread {
            val newUri = getFinalUriFromPath(path, applicationId) ?: return@ensureBackgroundThread
            Intent(this, EditActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                action = Intent.ACTION_EDIT
                setDataAndType(newUri, getUriMimeType(path, newUri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                val parent = path.getParentPath()
                val newFilename = "${path.getFilenameFromPath().substringBeforeLast('.')}_1"
                val extension = path.getFilenameExtension()
                val newFilePath = File(parent, "$newFilename.$extension")

                val outputUri = if (isPathOnOTG(path)) newUri else getFinalUriFromPath("$newFilePath", applicationId)
                val resInfoList = packageManager.queryIntentActivities(this, PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    grantUriPermission(packageName, outputUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
                putExtra(REAL_FILE_PATH, path)

                if (resolveActivity(packageManager) != null) {
                    try {
                        startActivityForResult(this, REQUEST_EDIT_IMAGE)
                    } catch (e: SecurityException) {
                        showErrorToast(e)
                    }
                } else {
                    toast(com.kr.commons.R.string.no_app_found)
                }
            }

            runOnUiThread {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                bottomSheet.expand(false)
            }
        }
    }

    private fun updateViewPager(mediaList: ArrayList<Medium>) {
        if(mediaList.isEmpty()){
            toast(R.string.no_image)
            if (!deleteBySwipeUp) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            return
        }

        mMediaFiles = mediaList.clone() as ArrayList<Medium>
        val newPos = getPositionInList(mediaList)
        if(newPos == -1) {
            if(mPos >= mMediaFiles.size)
                mPos = 0
            mPath = mMediaFiles[mPos].path
        }
        else {
            mPos = newPos
        }

        updateActionbarTitle()
        updatePagerItems(mMediaFiles)
        invalidateOptionsMenu()
    }

    //Edit activity????????? image??? ???????????? ??????????????????
    @SuppressLint("Recycle")
    private fun imageSavedAfterEditing(medium: Medium) {
        val path = medium.path

        //?????? ????????? ?????????????????????|?????? ????????? ??????????????????
        val overwritten = path == mPath

        //??????????????? Medium????????? ???????????? UI??? ????????????
        val newMediaList = ArrayList<Medium>(mMediaFiles)
        if(overwritten && mPos >= 0 && newMediaList.isNotEmpty()) {
            newMediaList.removeAt(mPos)
            newMediaList.add(mPos, medium)
        }
        else {
            newMediaList.add(medium)
            newMediaList.sortWith(compareByDescending<Medium> { it.dateTaken }.thenByDescending { it.modified }.thenBy { it.name })
        }
        mPath = path
        updateViewPager(newMediaList)
    }

    private fun getPositionInList(items: MutableList<Medium>): Int {
        for ((i, medium) in items.withIndex()) {
            val portraitPath = getPortraitPath()
            if (portraitPath != "") {
                val portraitPaths = File(portraitPath).parentFile?.list()
                if (portraitPaths != null) {
                    for (path in portraitPaths) {
                        if (medium.name == path) {
                            return i
                        }
                    }
                }
            } else if (medium.path == mPath) {
                return i
            }
        }
        return -1
    }

    override fun fragmentClicked() {
        mIsFullScreen = !mIsFullScreen
        checkSystemUI()                                 // setOnSystemUiVisibilityChangeListener call fullscreenToggled()
        fullscreenToggled()
    }

    override fun videoEnded(): Boolean {
        if (mIsSlideshowActive) {
            swipeToNextMedium()
        }
        return mIsSlideshowActive
    }

    override fun isSlideShowActive() = mIsSlideshowActive

    override fun launchViewVideoIntent(path: String) {
        window.decorView.setOnSystemUiVisibilityChangeListener(null)
        ensureBackgroundThread {
            val newUri = getFinalUriFromPath(path, BuildConfig.APPLICATION_ID)
                    ?: return@ensureBackgroundThread
            val mimeType = getUriMimeType(path, newUri)
            val position = mediaDB.getVideoTime(path).firstOrNull()

            Intent(applicationContext, VideoPlayerActivity::class.java).apply {
                setDataAndType(newUri, mimeType)
                putExtra(PATH, path)
                if(position != null)
                    putExtra(POSITION, position)
                startActivity(this)
            }
        }
    }

    private fun checkSystemUI() {
        if (mIsFullScreen) {
            hideSystemUI(true)
        } else {
            stopSlideshow()
            showSystemUI(true)
        }
    }

    private fun fullscreenToggled() {
        view_pager.adapter?.let {
            val pos = view_pager.currentItem
            val currentID = mAdapter.getItemId(pos)
            val centerFrag = supportFragmentManager.findFragmentByTag("f$currentID") as ViewPagerFragment?
            centerFrag?.fullscreenToggled(mIsFullScreen)

            val newAlpha = if (mIsFullScreen) 0f else 1f
            top_shadow.animate().alpha(newAlpha).start()
        }
        if(mIsFullScreen) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            bottomSheet.expand(false)

            mSavedBottomState = BottomSheetBehavior.STATE_COLLAPSED
        }
        else {
            if(!dialogOpened)
                bottomSheetBehavior.state = mSavedBottomState
            bottomSheet.expand(mSavedBottomState == BottomSheetBehavior.STATE_EXPANDED)
        }

        if (!mIsFullScreen) restartTimeOut()
    }

    private fun updateActionbarTitle() {
        runOnUiThread {
            if (mPos < getCurrentMedia().size) {
                supportActionBar?.title = getCurrentMedia()[mPos].path.getFilenameFromPath()
            }
        }
    }

    private fun getCurrentMedium(): Medium? {
        return if (getCurrentMedia().isEmpty() || mPos == -1) {
            null
        } else {
            getCurrentMedia()[Math.min(mPos, getCurrentMedia().size - 1)]
        }
    }

    private fun getCurrentMedia() = if (mAreSlideShowMediaVisible) mSlideshowMedia else mMediaFiles

    private fun getCurrentPath() = getCurrentMedium()?.path ?: ""

    private fun dp2px(dipValue: Float): Float {
        val m = resources.displayMetrics.density
        return dipValue * m
    }

    class CustPagerTransformer(context: Context, view_pager: ViewPager2, supportFragmentManager: FragmentManager) : ViewPager2.PageTransformer {
        private var viewPager: ViewPager2 = view_pager
        private var adapter: MyPagerAdapter = view_pager.adapter as MyPagerAdapter
        private var maxTranslateOffsetX = 0f
        private var mContext : Context = context

        private var mScale = 1f
        private var mSupportFragmentManager = supportFragmentManager

        // below views are used for delete/undo animation
        var leftBeforeFragContainer : View ?= null
        var leftFragContainer : View ?= null
        var currFragContainer : View ?= null
        var rightFragContainer : View ?= null
        var rightNextFragContainer : View ?= null

        override fun transformPage(page: View, position: Float) {
            val leftInScreen: Int = page.left - viewPager.scrollX

            val centerXInViewPager = leftInScreen + page.measuredWidth / 2
            val offsetX = centerXInViewPager - viewPager.measuredWidth / 2
            val offsetRate: Float = offsetX * 0.38f / viewPager.measuredWidth

            maxTranslateOffsetX = page.measuredWidth * (1 - mScale)

            val scaleFactor = 1 - Math.abs(offsetRate)
            if (scaleFactor > 0) {
                page.scaleX = mScale
                page.scaleY = mScale
                page.translationX = (maxTranslateOffsetX - dp2px(20f)) * -position
            }

            val pos = viewPager.currentItem
            val leftID = adapter.getItemId(pos - 1)
            val currentID = adapter.getItemId(pos)
            val rightID = adapter.getItemId(pos + 1)

            val leftFrag = mSupportFragmentManager.findFragmentByTag("f$leftID") as ViewPagerFragment?
            leftFrag?.updateStateZoom(mScale)
            leftFrag?.setIsCurrentFrag(false)
            val centerFrag = mSupportFragmentManager.findFragmentByTag("f$currentID") as ViewPagerFragment?
            centerFrag?.updateStateZoom(mScale)
            centerFrag?.setIsCurrentFrag(true)
            val rightFrag = mSupportFragmentManager.findFragmentByTag("f$rightID") as ViewPagerFragment?
            rightFrag?.updateStateZoom(mScale)
            rightFrag?.setIsCurrentFrag(false)

            when (position) {
                -2f -> leftBeforeFragContainer = page
                -1f -> leftFragContainer = page
                0f -> currFragContainer = page
                1f -> rightFragContainer = page
                2f -> rightNextFragContainer = page
            }
        }

        private fun dp2px(dipValue: Float): Float {
            val m = mContext.resources.displayMetrics.density
            return dipValue * m
        }

        fun setScale(scale: Float) {
            mScale = scale
        }

        fun getScale() : Float {
            return mScale
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        restartTimeOut()
    }
}
