package com.zfdang.chess

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.components.Description
import com.zfdang.chess.adapters.HistoryAndTrendAdapter
import com.zfdang.chess.controllers.ControllerListener
import com.zfdang.chess.controllers.GameController
import com.zfdang.chess.databinding.ActivityGameBinding
import com.zfdang.chess.gamelogic.GameStatus
import com.zfdang.chess.gamelogic.Move
import com.zfdang.chess.gamelogic.Piece
import com.zfdang.chess.gamelogic.Position
import com.zfdang.chess.gamelogic.Rule
import com.zfdang.chess.openbook.BHOpenBook
import com.zfdang.chess.utils.ToastUtils
import com.zfdang.chess.views.ChessView
import androidx.core.view.isVisible
import androidx.core.view.isGone
import com.zfdang.chess.receiver.CheckmateSmsReceiver
import com.zfdang.chess.tts.Speaker

class GameActivity : AppCompatActivity(), View.OnTouchListener, ControllerListener,
    View.OnClickListener, SettingDialogFragment.SettingDialogListener {

    // 防止重复点击
    private var curClickTime: Long = 0
    private var lastClickTime: Long = 0

    private lateinit var binding: ActivityGameBinding
    private lateinit var chessLayout: FrameLayout

    // 棋盘
    private lateinit var chessView: ChessView
    private lateinit var historyAndTrendAdapter: HistoryAndTrendAdapter

    // controller, player, game
    private lateinit var controller: GameController

    // mediaplayer
    private lateinit var soundPlayer: SoundPlayer

    private lateinit var bhBook: BHOpenBook

    private var isFromManual = false

    private var isRemoteGame = false

    private val TAG = GameActivity::class.java.simpleName

    private val speaker = Speaker()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Disable screen saver
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ToastUtils.coordinatorLayout = binding.root

        // new game
        controller = GameController(this)
        controller.loadGameStatus()

        // 初始化棋盘
        chessLayout = binding.chesslayout
        chessView = ChessView(this, controller)
        chessLayout.addView(chessView)
        chessView.setOnTouchListener(this)

        bhBook = BHOpenBook(this)

        // Bind all imagebuttons here, and set their onClickListener
        val imageButtons = listOf(
            binding.playerbt,
            binding.playerbackbt,
            binding.playerforwardbt,
            binding.autoplaybt,
            binding.quickbt,
            binding.playeraltbt,
            binding.optionbt,
            binding.newbt,
            binding.backbt,
            binding.importbt,
            binding.exportbt,
            binding.helpbt,
            binding.stophelpbt,
            binding.trendsbt,
            binding.exitbt,
            binding.choice1bt,
            binding.choice2bt,
            binding.choice3bt
        )
        for (button in imageButtons) {
            button.setOnClickListener(this)
        }

        // init audio files
        soundPlayer = SoundPlayer(this, controller)

        // Bind historyTable and initialize it with dummy data
        val historyTable = binding.historyTable
        val chart = binding.trendchart
        historyAndTrendAdapter = HistoryAndTrendAdapter(this, historyTable, chart, controller)
        historyAndTrendAdapter.update()

        // customize chart
        chart.description = Description().apply {
            text = ""
        }
        val xAxis = chart.xAxis
        xAxis.isGranularityEnabled = true
        xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        val yAxis = chart.axisLeft
        yAxis.setDrawGridLines(false)
        yAxis.setDrawZeroLine(true)
        val rightAxis = chart.axisRight
        rightAxis.setDrawGridLines(false)

        // init button status
        if(controller.isAutoPlay) {
            binding.autoplaybt.setImageResource(R.drawable.play_circle)
        } else {
            binding.autoplaybt.setImageResource(R.drawable.pause_circle)
        }
        if(controller.isComputerPlaying){
            binding.playerbt.setImageResource(R.drawable.computer)
        } else {
            binding.playerbt.setImageResource(R.drawable.person)
        }

        // init status text
        if(controller.isRedTurn){
            setStatusText("等待红方走棋")
        } else if(controller.isBlackTurn) {
            setStatusText("等待黑方走棋")
        }

        // receive parameters from intent
        val fenString = intent.getStringExtra("FENString")
        if(fenString != null){
            controller.startFENGame(fenString)
            isFromManual = true
            controller.toggleComputerAutoPlay()
            binding.autoplaybt.setImageResource(R.drawable.pause_circle)
        }

        CheckmateSmsReceiver.onNewMessage = {
            message ->
                runOnUiThread {
                    handleCheckmateClientMessage(message)
                }
        }
        CheckmateSmsReceiver.startReceiving()
        speaker.initTextToSpeech(this)
        // Globals.messenger.startProcessingMessages()
    }

    override fun onDestroy() {
        super.onDestroy()
        CheckmateSmsReceiver.stopReceiving()
        // Globals.messenger.close()
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (isRemoteGame) {
            showRemoteGameWarning()
            return false
        }

        // 防止重复点击
        lastClickTime = System.currentTimeMillis()
        if (lastClickTime - curClickTime < MIN_CLICK_DELAY_TIME) {
            return false
        }
        curClickTime = lastClickTime

        if (event!!.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y
            val pos = chessView.getPosByCoord(x, y)
                ?: // pos is not valid
                return false
            controller.touchPosition(pos)
            v?.performClick()
            Log.d("PlayActivity", "onTouch: x = $x, y = $y, pos = $pos")
        }
        return false
    }

    // create function to set status text
    private fun setStatusText(text: String) {
        binding.statustv.text = text
    }

    private fun startNewGame() {
        controller.startNewGame()
        historyAndTrendAdapter.update()
        if(controller.settings.red_go_first) {
            setStatusText("新游戏，红方先行")
        } else {
            setStatusText("新游戏，黑方先行")
        }
        // hide choice buttons
        if(binding.choice1bt.isVisible){
            binding.choice1bt.visibility = View.GONE
            binding.choice2bt.visibility = View.GONE
            binding.choice3bt.visibility = View.GONE
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if(controller.isComputerPlaying && controller.isAutoPlay && !controller.settings.red_go_first){
                controller.computerForward()
            }
        }, 1000)
    }

    private fun showNewGameConfirmDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("确定开始新游戏?")
        builder.setMessage("你是否要放弃当前的游戏，开始新游戏呢?")

        builder.setPositiveButton("开始新游戏") { _, _ ->
            // User clicked Yes button
            isRemoteGame = false
            startNewGame()
        }

        builder.setNegativeButton("继续当前游戏") { dialog, _ ->
            // User clicked No button
            dialog.dismiss()
        }

        builder.setCancelable(true)
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    private fun saveThenExit() {
        // in case there is any ongoing searching task
        controller.player.stopSearch()
        // delay 300 ms to save game status
        Thread.sleep(100)
        if(!isFromManual){
            // 如果从打谱界面进入，不保存游戏状态
            controller.saveGameStatus()
        }
        finish()
    }

    private fun showInputFENDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("从FEN串开始新游戏")
        builder.setMessage("请输入棋局的FEN串：")

        // Set up the input
        val input = EditText(this)

        // get content from clipboard
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text
            input.setText(text)
        }
        input.setSelection(input.text.length)
        input.setSelectAllOnFocus(true)

        builder.setView(input)

        // Set up the buttons
        builder.setPositiveButton("确定") { _, _ ->
            val userInput = input.text.toString()
            controller.startFENGame(userInput)
            // Handle the input string here
        }
        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    override fun onClick(v: View?) {
        // handle events for all imagebuttons in activity_player.xml
        when(v) {
            binding.playerbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                controller.toggleComputer()
                if(controller.isComputerPlaying){
                    binding.playerbt.setImageResource(R.drawable.computer)
                    setStatusText("切换为电脑执黑棋")
                } else {
                    binding.playerbt.setImageResource(R.drawable.person)
                    setStatusText("切换为人工执黑棋")
                }
            }
            binding.playerbackbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                controller.stepBack()
            }
            binding.playerforwardbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                controller.computerForward()
            }
            binding.autoplaybt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                controller.toggleComputerAutoPlay()
                if(controller.isAutoPlay){
                    binding.autoplaybt.setImageResource(R.drawable.play_circle)
                    setStatusText("开启自动走棋")

                    if(controller.isBlackTurn && controller.isComputerPlaying){
                        // 如果电脑执黑，自动走棋
                        controller.computerForward()
                    }
                } else {
                    binding.autoplaybt.setImageResource(R.drawable.pause_circle)
                    setStatusText("暂停自动走棋")
                }
            }
            binding.quickbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                controller.stopSearchNow()
            }
            binding.playeraltbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                controller.computerAskForMultiPV()
            }
            binding.optionbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                // Show the dialog
                val dialog = SettingDialogFragment()
                dialog.setController(controller)
                dialog.listener = this
                dialog.setEngineInfo(controller.engineInfo)
                dialog.show(supportFragmentManager, "CustomDialog")
            }
            binding.newbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                // display dialog to ask users for confirmation
                showNewGameConfirmDialog()
            }
            binding.backbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                controller.stepBack()
            }
            binding.importbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                showInputFENDialog()
            }
            binding.exportbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                val fenString = controller.game.currentBoard.toFENString()
                // copy to clipboard
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("FEN", fenString)
                clipboard.setPrimaryClip(clip)
                setStatusText("FEN串已复制到剪贴板")
                Log.d("GameActivity", "fenString: $fenString")
            }
            binding.helpbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                setStatusText("正在搜索建议着法...")
                controller.playerAskForHelp()
            }
            binding.stophelpbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                controller.stopSearchNow()
            }
            binding.trendsbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                // get image resource of trends button
                controller.toggleShowTrends()
                val imageResource = if(controller.isShowTrends) R.drawable.trend else R.drawable.history
                binding.trendsbt.setImageResource(imageResource)

                if(controller.isShowTrends){
                    setStatusText("显示评估趋势图")
                    binding.trendchart.visibility = View.VISIBLE
                    binding.historyscroll.visibility = View.GONE
                } else {
                    setStatusText("显示走法历史")
                    binding.trendchart.visibility = View.GONE
                    binding.historyscroll.visibility = View.VISIBLE
                }

                historyAndTrendAdapter.update()
            }
            binding.exitbt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                saveThenExit()
            }
            binding.choice1bt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                setStatusText("选择着数1")
                binding.choice1bt.visibility = View.GONE
                binding.choice2bt.visibility = View.GONE
                binding.choice3bt.visibility = View.GONE
                controller.selectMultiPV(0)
            }
            binding.choice2bt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                setStatusText("选择着数2")
                binding.choice1bt.visibility = View.GONE
                binding.choice2bt.visibility = View.GONE
                binding.choice3bt.visibility = View.GONE
                controller.selectMultiPV(1)
            }
            binding.choice3bt -> {
                if (isRemoteGame) {
                    showRemoteGameWarning()
                    return
                }

                setStatusText("选择着数3")
                binding.choice1bt.visibility = View.GONE
                binding.choice2bt.visibility = View.GONE
                binding.choice3bt.visibility = View.GONE
                controller.selectMultiPV(2)
            }
        }

    }

    override fun onGameEvent(status: GameStatus?, message: String?) {
        Log.d(  "PlayActivity", "onGameEvent: $status, $message")
        when(status) {
            GameStatus.ILLEGAL -> {
                message?.let { setStatusText(it) }
                soundPlayer.illegal()
            }
            GameStatus.MOVE -> {
                message?.let { setStatusText(it) }
                soundPlayer.move()

                if(binding.choice1bt.isVisible){
                    binding.choice1bt.visibility = View.GONE
                    binding.choice2bt.visibility = View.GONE
                    binding.choice3bt.visibility = View.GONE
                }
            }
            GameStatus.CAPTURE -> {
                message?.let { setStatusText(it) }
                soundPlayer.capture()
            }
            GameStatus.CHECK -> {
                message?.let { setStatusText(it) }
                soundPlayer.check()
            }
            GameStatus.CHECKMATE -> {
                message?.let { setStatusText(it) }
                soundPlayer.checkmate()
            }
            GameStatus.SELECT -> {
                message?.let { setStatusText(it) }
                soundPlayer.select()
            }
            GameStatus.WIN -> message?.let { setStatusText(it) }
            GameStatus.LOSE -> message?.let { setStatusText(it) }
            GameStatus.DRAW -> message?.let { setStatusText(it) }
            GameStatus.ENGINE -> message?.let { setStatusText(it) }
            null -> TODO()
            GameStatus.MULTIPV -> {
                // show message
                message?.let { setStatusText(it) }

                // show choice buttons
                if(binding.choice1bt.isGone){
                    binding.choice1bt.visibility = View.VISIBLE
                    if(controller.multiPVSize >= 2){
                        binding.choice2bt.visibility = View.VISIBLE
                    }
                    if(controller.multiPVSize >= 3){
                        binding.choice3bt.visibility = View.VISIBLE
                    }
                }

                soundPlayer.ready()
            }
            GameStatus.UPDATEUI -> {
                message?.let { setStatusText(message) }
                // do nothing here
            }
        }

        // update history table
        historyAndTrendAdapter.update()
    }

    @Deprecated("Only for backwards compatability")
    override fun onBackPressed() {
        if (isRemoteGame) {
            showRemoteGameWarning()
            return
        }

        // save game to file
        super.onBackPressed()
        saveThenExit()
    }

    override fun onGameEvent(event: GameStatus?) = onGameEvent(event, null)

    override fun runOnUIThread(runnable: Runnable?) = runOnUiThread(runnable)

    override fun isRemoteGame(): Boolean = isRemoteGame

    override fun onDialogPositiveClick() {
        // save setting values to variables in settings
        Log.d("GameActivity", "onDialogPositiveClick" + controller.settings.toString())
    }

    override fun onDialogNegativeClick() {
        // do nothing
    }

    companion object Constants {
        const val COMMAND_NEW_GAME_WITH_RED_FIRST: String = "1"
        const val COMMAND_NEW_GAME_WITH_DARK_FIRST: String = "2"
        const val COMMAND_END_REMOTE_GAME: String = "3"
        const val COMMAND_WITHDRAW_LAST_MOVE: String = "4"
        const val MIN_CLICK_DELAY_TIME: Int = 100
    }

    private fun showRemoteGameWarning() {
        ToastUtils.showSnackbar("远程棋局不允许手动下棋")
    }

    private fun handleCheckmateClientMessage(message: String) {
        when (message) {
            COMMAND_NEW_GAME_WITH_RED_FIRST -> {
                isRemoteGame = true
                ToastUtils.showSnackbar("新远程棋局, 红方(用户)先行")
                controller.settings.red_go_first = true
                startNewGame()
                speaker.speak("新红方先行远程棋局已开始")
            }
            COMMAND_NEW_GAME_WITH_DARK_FIRST -> {
                isRemoteGame = true
                ToastUtils.showSnackbar("新远程棋局, 黑方(电脑)先行")
                controller.settings.red_go_first = false
                startNewGame()
                speaker.speak("新电脑先行远程棋局已开始")
            }
            COMMAND_WITHDRAW_LAST_MOVE -> {
                if (!isRemoteGame) {
                    return
                }
                ToastUtils.showSnackbar("撤销上一步棋")
                withdrawLastMoveDelayed()
            }
            COMMAND_END_REMOTE_GAME -> {
                if (!isRemoteGame) {
                    return
                }
                ToastUtils.showSnackbar("结束远程棋局")
                // 开始新默认棋局
                isRemoteGame = false
                controller.settings.red_go_first = true
                startNewGame()
                speaker.speak("远程棋局已结束")
            }
            else -> {
                if (!isRemoteGame) {
                    return
                }

                if (isChineseChessMove(message)) {
                    ToastUtils.showSnackbar("收到红方(用户)着法$message")
                    val move = parseMoveMessage(message)
                    moveRedDelayed(move)
                } else {
                    sendInvalidMove()
                }
            }
        }
    }

    private fun sendInvalidMove() {
        speaker.speak("无效着法")
        ToastUtils.showSnackbar("无效着法")
    }

    private fun moveRedDelayed(move: Move) {
        if (controller.state != GameController.ControllerState.WAITING_FOR_USER) {
            Handler(Looper.getMainLooper()).postDelayed({
                moveRedDelayed(move)
            }, 3000)
            return
        }

        moveRed(move)
    }

    private fun moveRed(move: Move) {
        val game = controller.game
        val board = game.currentBoard

        // If game's start position is not empty, clear it first
        if (game.startPos != null) {
            game.clearStartPos()
            onGameEvent(GameStatus.SELECT, "取消选择棋子")
        }
        if (move.fromPosition.equals(move.toPosition)) {
            sendInvalidMove()
            return
        }
        val piece = board.getPieceByPosition(move.fromPosition)
        if (Piece.isValid(piece) && Piece.isRed(piece)) {
            val tempMove = Move(move.fromPosition, move.toPosition, board)
            if (Rule.isValidMove(tempMove, board)) {
                val traditionalMoveDesc = tempMove.chsString
                speaker.speak("远程对手走棋$traditionalMoveDesc")

                controller.touchPosition(move.fromPosition)
                controller.touchPosition(move.toPosition)

                return
            }
        }

        sendInvalidMove()
    }

    private fun withdrawLastMoveDelayed() {
        if (controller.state != GameController.ControllerState.WAITING_FOR_USER) {
            Handler(Looper.getMainLooper()).postDelayed({
                withdrawLastMoveDelayed()
            }, 3000)
            return
        }

        controller.stepBack()
        controller.stepBack()
    }

    private fun isChineseChessMove(text: String): Boolean = text.length == 4 && text.all { it.isDigit() }

    private fun parseMoveMessage(moveText: String): Move {
        val fromPosition = Position(moveText[0].digitToInt(), moveText[1].digitToInt())
        val toPosition = Position(moveText[2].digitToInt(), moveText[3].digitToInt())
        return Move(fromPosition, toPosition)
    }
}