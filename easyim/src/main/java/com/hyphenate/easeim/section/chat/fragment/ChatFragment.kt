package com.hyphenate.easeim.section.chat.fragment

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.text.TextUtils
import android.view.View
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.ViewModelProvider
import com.hyphenate.EMError
import com.hyphenate.chat.EMClient
import com.hyphenate.chat.EMMessage
import com.hyphenate.chat.EMTextMessageBody
import com.hyphenate.easeim.DemoHelper
import com.hyphenate.easeim.R
import com.hyphenate.easeim.common.constant.DemoConstant
import com.hyphenate.easeim.common.livedatas.LiveDataBus
import com.hyphenate.easeim.common.model.EmojiconExampleGroupData
import com.hyphenate.easeim.common.utils.ToastUtils
import com.hyphenate.easeim.section.base.BaseActivity
import com.hyphenate.easeim.section.chat.activity.*
import com.hyphenate.easeim.section.chat.fragment.ChatFragment
import com.hyphenate.easeim.section.chat.viewmodel.MessageViewModel
import com.hyphenate.easeim.section.conference.ConferenceActivity
import com.hyphenate.easeim.section.contact.activity.ContactDetailActivity
import com.hyphenate.easeim.section.dialog.DemoListDialogFragment
import com.hyphenate.easeim.section.dialog.FullEditDialogFragment
import com.hyphenate.easeim.section.group.GroupHelper
import com.hyphenate.easeui.constants.EaseConstant
import com.hyphenate.easeui.domain.EaseUser
import com.hyphenate.easeui.manager.EaseThreadManager
import com.hyphenate.easeui.model.EaseEvent
import com.hyphenate.easeui.ui.EaseChatFragment
import com.hyphenate.easeui.ui.EaseChatFragment.OnMessageChangeListener
import com.hyphenate.easeui.widget.EaseChatInputMenu
import com.hyphenate.easeui.widget.emojicon.EaseEmojiconMenu
import com.hyphenate.exceptions.HyphenateException
import com.hyphenate.util.EMLog
import com.hyphenate.util.PathUtil
import com.hyphenate.util.UriUtils
import java.io.File
import java.io.FileOutputStream

class ChatFragment : EaseChatFragment(), OnMessageChangeListener {
    private var viewModel: MessageViewModel? = null
    protected var clipboard: ClipboardManager? = null
    override fun initChildView() {
        super.initChildView()
        clipboard = activity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        viewModel = ViewModelProvider(this).get(
            MessageViewModel::class.java
        )
    }

    override fun initChildListener() {
        super.initChildListener()
        setOnMessageChangeListener(this)
    }

    override fun initChildData() {
        super.initChildData()
        inputMenu.insertText(unSendMsg)
        LiveDataBus.get().with(DemoConstant.MESSAGE_CHANGE_CHANGE)
            .postValue(EaseEvent(DemoConstant.MESSAGE_CHANGE_CHANGE, EaseEvent.TYPE.MESSAGE))
        LiveDataBus.get().with(DemoConstant.MESSAGE_CALL_SAVE, Boolean::class.java).observe(
            viewLifecycleOwner, { event: Boolean? ->
                if (event == null) {
                    return@observe
                }
                if (event) {
                    //chatMessageList.refreshToLatest()
                }
            })
        LiveDataBus.get().with(DemoConstant.CONVERSATION_DELETE, EaseEvent::class.java).observe(
            viewLifecycleOwner, { event: EaseEvent? ->
                if (event == null) {
                    return@observe
                }
                if (event.isMessageChange) {
                    //chatMessageList.refreshMessages()
                }
            })
    }

    override fun openTurnOnTyping(): Boolean {
        return DemoHelper.getInstance().model.isShowMsgTyping
    }

    /**
     * 为了重排默认扩展功能顺序，需要重写此方法，并调用[EaseChatExtendMenu.init]
     */
    override fun initExtendInputMenu() {
        inputMenu.init()
        //inputMenu.setHint(R.string.em_chat_et_hint);
    }

    override fun addExtendInputMenu(inputMenu: EaseChatInputMenu) {
        super.addExtendInputMenu(inputMenu)
        inputMenu.registerExtendMenuItem(
            R.string.attach_picture,
            R.drawable.ease_chat_image_selector,
            EaseChatInputMenu.ITEM_PICTURE,
            this
        )
        inputMenu.registerExtendMenuItem(
            R.string.attach_take_pic,
            R.drawable.ease_chat_takepic_selector,
            EaseChatInputMenu.ITEM_TAKE_PICTURE,
            this
        )
        inputMenu.registerExtendMenuItem(
            R.string.attach_video,
            R.drawable.em_chat_video_selector,
            EaseChatInputMenu.ITEM_VIDEO,
            this
        )
        //添加扩展槽
        if (chatType == EaseConstant.CHATTYPE_SINGLE) {
            //inputMenu.registerExtendMenuItem(R.string.attach_voice_call, R.drawable.em_chat_voice_call_selector, EaseChatInputMenu.ITEM_VOICE_CALL, this);
            inputMenu.registerExtendMenuItem(
                R.string.attach_media_call,
                R.drawable.em_chat_video_call_selector,
                EaseChatInputMenu.ITEM_VIDEO_CALL,
                this
            )
        }
        if (chatType == EaseConstant.CHATTYPE_GROUP) { // 音视频会议
            inputMenu.registerExtendMenuItem(
                R.string.voice_and_video_conference,
                R.drawable.em_chat_video_call_selector,
                EaseChatInputMenu.ITEM_CONFERENCE_CALL,
                this
            )
            //目前普通模式也支持设置主播和观众人数，都建议使用普通模式
            //inputMenu.registerExtendMenuItem(R.string.title_live, R.drawable.em_chat_video_call_selector, EaseChatInputMenu.ITEM_LIVE, this);
        }
        inputMenu.registerExtendMenuItem(
            R.string.attach_location,
            R.drawable.ease_chat_location_selector,
            EaseChatInputMenu.ITEM_LOCATION,
            this
        )
        inputMenu.registerExtendMenuItem(
            R.string.attach_file,
            R.drawable.em_chat_file_selector,
            EaseChatInputMenu.ITEM_FILE,
            this
        )
        //群组类型，开启消息回执，且是owner
        if (chatType == EaseConstant.CHATTYPE_GROUP && EMClient.getInstance().options.requireAck) {
            val group = DemoHelper.getInstance().groupManager.getGroup(toChatUsername)
            if (GroupHelper.isOwner(group)) {
                inputMenu.registerExtendMenuItem(
                    R.string.em_chat_group_delivery_ack,
                    R.drawable.demo_chat_delivery_selector,
                    ITEM_DELIVERY,
                    this
                )
            }
        }
        //添加扩展表情
        (inputMenu.emojiconMenu as EaseEmojiconMenu).addEmojiconGroup(EmojiconExampleGroupData.getData())
    }

    override fun onChatExtendMenuItemClick(itemId: Int, view: View) {
        super.onChatExtendMenuItemClick(itemId, view)
        when (itemId) {
            EaseChatInputMenu.ITEM_VIDEO_CALL ->                 //startVideoCall();
                showSelectDialog()
            EaseChatInputMenu.ITEM_CONFERENCE_CALL -> ConferenceActivity.startConferenceCall(
                activity, toChatUsername
            )
            EaseChatInputMenu.ITEM_LIVE -> LiveActivity.startLive(context, toChatUsername)
            ITEM_DELIVERY -> showDeliveryDialog()
        }
    }

    private fun showDeliveryDialog() {
        FullEditDialogFragment.Builder(mContext as BaseActivity)
            .setTitle(R.string.em_chat_group_read_ack)
            .setOnConfirmClickListener(R.string.em_chat_group_read_ack_send) { view, content ->
                sendTextMessage(
                    content,
                    true
                )
            }
            .setConfirmColor(R.color.em_color_brand)
            .setHint(R.string.em_chat_group_read_ack_hint)
            .show()
    }

    private fun showSelectDialog() {
        DemoListDialogFragment.Builder(mContext as BaseActivity) //.setTitle(R.string.em_single_call_type)
            .setData(calls)
            .setCancelColorRes(R.color.black)
            .setWindowAnimations(R.style.animate_dialog)
            .setOnItemClickListener { view, position ->
                when (position) {
                    0 -> startVideoCall()
                    1 -> startVoiceCall()
                }
            }
            .show()
    }

    override fun onUserAvatarClick(username: String) {
        super.onUserAvatarClick(username)
        if (!TextUtils.equals(username, DemoHelper.getInstance().currentUser)) {
            val user = EaseUser()
            user.username = username
            ContactDetailActivity.actionStart(mContext, user)
        }
    }

    override fun onBubbleLongClick(v: View, message: EMMessage) {
        super.onBubbleLongClick(v, message)
        val menu = PopupMenu(mContext, v)
        menu.menuInflater.inflate(R.menu.demo_chat_list_menu, menu.menu)
        val menuPopupHelper = MenuPopupHelper(mContext, (menu.menu as MenuBuilder), v)
        menuPopupHelper.setForceShowIcon(true)
        menuPopupHelper.show()
        setMenuByMsgType(message, menu)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_chat_copy -> clipboard!!.setPrimaryClip(
                    ClipData.newPlainText(
                        null,
                        (message.body as EMTextMessageBody).message
                    )
                )
                R.id.action_chat_delete -> {
                    if (messageChangeListener != null) {
                        val event = EaseEvent.create(
                            DemoConstant.MESSAGE_CHANGE_DELETE,
                            EaseEvent.TYPE.MESSAGE
                        )
                        messageChangeListener.onMessageChange(event)
                    }
                    conversation.removeMessage(message.msgId)
                    removeMessage(message)
                }
                R.id.action_chat_forward -> ForwardMessageActivity.actionStart(
                    mContext,
                    message.msgId
                )
                R.id.action_chat_recall -> {
                    if (messageChangeListener != null) {
                        val event = EaseEvent.create(
                            DemoConstant.MESSAGE_CHANGE_RECALL,
                            EaseEvent.TYPE.MESSAGE
                        )
                        messageChangeListener.onMessageChange(event)
                    }
                    recallMessage(message)
                }
            }
            false
        }
    }

    private fun recallMessage(message: EMMessage) {
        EaseThreadManager.getInstance().runOnIOThread {
            try {
                val msgNotification = EMMessage.createSendMessage(EMMessage.Type.TXT)
                val txtBody = EMTextMessageBody(resources.getString(R.string.msg_recall_by_self))
                msgNotification.addBody(txtBody)
                msgNotification.to = message.to
                msgNotification.msgTime = message.msgTime
                msgNotification.setLocalTime(message.msgTime)
                msgNotification.setAttribute(DemoConstant.MESSAGE_TYPE_RECALL, true)
                msgNotification.setStatus(EMMessage.Status.SUCCESS)
                EMClient.getInstance().chatManager().recallMessage(message)
                EMClient.getInstance().chatManager().saveMessage(msgNotification)
                refreshMessages()
            } catch (e: HyphenateException) {
                e.printStackTrace()
                if (isActivityDisable) {
                    return@runOnIOThread
                }
                mContext.runOnUiThread { ToastUtils.showToast(e.message) }
            }
        }
    }

    private fun setMenuByMsgType(message: EMMessage, menu: PopupMenu) {
        val type = message.type
        menu.menu.findItem(R.id.action_chat_copy).isVisible = false
        menu.menu.findItem(R.id.action_chat_forward).isVisible = false
        menu.menu.findItem(R.id.action_chat_recall).isVisible = false
        when (type) {
            EMMessage.Type.TXT -> if (message.getBooleanAttribute(
                    DemoConstant.MESSAGE_ATTR_IS_VIDEO_CALL,
                    false
                )
                || message.getBooleanAttribute(DemoConstant.MESSAGE_ATTR_IS_VOICE_CALL, false)
            ) {
                menu.menu.findItem(R.id.action_chat_recall).isVisible = true
            } else if (message.getBooleanAttribute(
                    DemoConstant.MESSAGE_ATTR_IS_BIG_EXPRESSION,
                    false
                )
            ) {
                menu.menu.findItem(R.id.action_chat_forward).isVisible = true
                menu.menu.findItem(R.id.action_chat_recall).isVisible = true
            } else {
                menu.menu.findItem(R.id.action_chat_copy).isVisible = true
                menu.menu.findItem(R.id.action_chat_forward).isVisible = true
                menu.menu.findItem(R.id.action_chat_recall).isVisible = true
            }
            EMMessage.Type.LOCATION, EMMessage.Type.FILE -> menu.menu.findItem(R.id.action_chat_recall).isVisible =
                true
            EMMessage.Type.IMAGE -> {
                menu.menu.findItem(R.id.action_chat_forward).isVisible = true
                menu.menu.findItem(R.id.action_chat_recall).isVisible = true
            }
            EMMessage.Type.VOICE -> {
                menu.menu.findItem(R.id.action_chat_delete).setTitle(R.string.delete_voice)
                menu.menu.findItem(R.id.action_chat_recall).isVisible = true
            }
            EMMessage.Type.VIDEO -> {
                menu.menu.findItem(R.id.action_chat_delete).setTitle(R.string.delete_video)
                menu.menu.findItem(R.id.action_chat_recall).isVisible = true
            }
        }
        if (chatType == DemoConstant.CHATTYPE_CHATROOM) {
            menu.menu.findItem(R.id.action_chat_forward).isVisible = false
        }
        if (message.direct() == EMMessage.Direct.RECEIVE) {
            menu.menu.findItem(R.id.action_chat_recall).isVisible = false
        }
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        if (!isGroupChat) {
            return
        }
        if (count == 1 && "@" == s[start].toString()) {
            PickAtUserActivity.actionStartForResult(
                this@ChatFragment,
                toChatUsername,
                REQUEST_CODE_SELECT_AT_USER
            )
        }
    }

    override fun selectVideoFromLocal() {
        super.selectVideoFromLocal()
        val intent = Intent(activity, ImageGridActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_SELECT_VIDEO)
    }

    override fun showMessageError(code: Int, error: String) {
        if (code == EMError.FILE_TOO_LARGE) {
            ToastUtils.showToast(R.string.demo_error_file_too_large)
        } else {
            ToastUtils.showToast("onError: $code, error: $error")
        }
    }

    override fun showMsgToast(message: String) {
        super.showMsgToast(message)
        ToastUtils.showToast(message)
    }

    override fun onMessageChange(change: EaseEvent) {
        viewModel!!.setMessageChange(change)
    }

    override fun sendMessage(message: EMMessage) {
        super.sendMessage(message)
        //消息变动，通知刷新
        LiveDataBus.get().with(DemoConstant.MESSAGE_CHANGE_CHANGE)
            .postValue(EaseEvent(DemoConstant.MESSAGE_CHANGE_CHANGE, EaseEvent.TYPE.MESSAGE))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_SELECT_AT_USER -> if (data != null) {
                    val username = data.getStringExtra("username")
                    inputAtUsername(username, false)
                }
                REQUEST_CODE_SELECT_VIDEO -> if (data != null) {
                    val duration = data.getIntExtra("dur", 0)
                    val videoPath = data.getStringExtra("path")
                    val uriString = data.getStringExtra("uri")
                    EMLog.d(TAG, "path = $videoPath uriString = $uriString")
                    if (!TextUtils.isEmpty(videoPath)) {
                        val file = File(
                            PathUtil.getInstance().videoPath,
                            "thvideo" + System.currentTimeMillis() + ".jpeg"
                        )
                        try {
                            val fos = FileOutputStream(file)
                            val ThumbBitmap = ThumbnailUtils.createVideoThumbnail(
                                videoPath!!, 3
                            )
                            ThumbBitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                            fos.close()
                            sendVideoMessage(videoPath, file.absolutePath, duration)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            EMLog.e(TAG, e.message)
                        }
                    } else {
                        val videoUri = UriUtils.getLocalUriFromString(uriString)
                        val file = File(
                            PathUtil.getInstance().videoPath,
                            "thvideo" + System.currentTimeMillis() + ".jpeg"
                        )
                        try {
                            val fos = FileOutputStream(file)
                            val media = MediaMetadataRetriever()
                            media.setDataSource(context, videoUri)
                            val frameAtTime = media.frameAtTime
                            frameAtTime!!.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                            fos.close()
                            sendVideoMessage(videoUri, file.absolutePath, duration)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        //保存未发送的文本消息内容
        if (mContext != null && mContext.isFinishing) {
            if (inputMenu != null) {
                saveUnSendMsg(inputMenu.inputContent)
                LiveDataBus.get().with(DemoConstant.MESSAGE_NOT_SEND).postValue(true)
            }
        }
    }
    //================================== for video and voice start ====================================
    /**
     * start video call
     */
    protected fun startVideoCall() {
        if (!EMClient.getInstance().isConnected) {
            showMsgToast(resources.getString(com.hyphenate.easeui.R.string.not_connect_to_server))
        } else {
            startChatVideoCall()
            // videoCallBtn.setEnabled(false);
            inputMenu.hideExtendMenuContainer()
        }
    }

    /**
     * start voice call
     */
    protected fun startVoiceCall() {
        if (!EMClient.getInstance().isConnected) {
            showMsgToast(resources.getString(com.hyphenate.easeui.R.string.not_connect_to_server))
        } else {
            startChatVoiceCall()
            // voiceCallBtn.setEnabled(false);
            inputMenu.hideExtendMenuContainer()
        }
    }

    protected fun startChatVideoCall() {
        ChatVideoCallActivity.actionStart(mContext, toChatUsername)
    }

    protected fun startChatVoiceCall() {
        ChatVoiceCallActivity.actionStart(mContext, toChatUsername)
    }
    //================================== for video and voice end ====================================
    //================================== for store do not send input logic start ====================================
    /**
     * 保存未发送的文本消息内容
     * @param content
     */
    private fun saveUnSendMsg(content: String) {
        DemoHelper.getInstance().model.saveUnSendMsg(toChatUsername, content)
    }

    //================================== for store do not send input logic end ====================================
    private val unSendMsg: String
        private get() = DemoHelper.getInstance().model.getUnSendMsg(toChatUsername)

    companion object {
        private val TAG = ChatFragment::class.java.simpleName
        private const val REQUEST_CODE_SELECT_AT_USER = 15
        private const val ITEM_DELIVERY = 10
        private val calls = arrayOf("视频通话", "语音通话")
    }
}