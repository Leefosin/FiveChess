package com.example.fivechessfront.Entity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.util.Log;

import com.example.fivechessfront.Activity.MainActivity;
import com.example.fivechessfront.Entity.Impl.CyberHuman;
import com.example.fivechessfront.Enums.GameType;
import com.example.fivechessfront.Enums.PlayerType;
import com.example.fivechessfront.Network.Client;
import com.example.fivechessfront.UIHelper.GameUIHelper;
import com.example.fivechessfront.Entity.Impl.AI;
import com.example.fivechessfront.utils.AccountManager;
import com.example.fivechessfront.Entity.Impl.Human;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class Game {
    private Player player1;
    private Player player2;

    public Board getBoard() {
        return board;
    }

    private final Board board;
    private Player currentPlayer;
    private Player winner;
    private AiThread aiThread;
    private int turns = 0;
    private GameUIHelper helper;
    private GameHistory gameHistory;
    private GameType type;

    private Context context;

    public Game(GameUIHelper helper, GameHistory gameHistory, Context context) {
        board = new Board();
        this.helper = helper;
        this.gameHistory = gameHistory;
        this.context = context;
    }

    public void SetGameType(GameType type,int difficulty) {
        this.type = type;
        String name = AccountManager.getInstance().getAccount().name;
        switch (type) {
            case PlayerVsPlayer:
                player1 = new Human(name, this);
                player2 = new Human(name + "的伙伴", this);
                break;
            case PlayerVsAi:
                player1 = new Human(name, this);
                player2 = new AI(difficulty, this);
                break;
            case PlayerVsInternet:
                player1 = new Human(name,this);
                player2 = new CyberHuman(this);
                Client.getInstance().setCyberHuman((CyberHuman) player2);
                break;
        }
    }


    public void Start() {
        assignRandomColors();// 随机分配玩家的棋子颜色
        if (getCurrentPlayer().getPlayerType() == PlayerType.AI) {
            Log.d("Game", "开启了ai");
            StartAi();
        }
        if(type != GameType.PlayerVsInternet) {
            if (player1.getPieceType() == 1) gameHistory.setColor("执黑");//在数据库中插入棋子的颜色
            else gameHistory.setColor("执白");
            gameHistory.name = player1.getName() + " vs " + player2.getName();
            helper.SetName(currentPlayer.getName());
        }
    }

    public void Restart() {
        turns = 0;
        board.ResetBoard();
        helper.Invalidate();
        gameHistory.Clear();
        Start();
    }

    public void RunATurn() {
        currentPlayer.Drops();
        if(player2.getPlayerType() == PlayerType.CyberHuman&&currentPlayer== player1){
            Client.getInstance().SendPosition(currentPlayer.getIntention());
        }
        gameHistory.WriteProcess(currentPlayer.getIntention());
        helper.Invalidate();
        ContinueDetect(currentPlayer.getIntention());
    }

    public void PassIntention(int row, int col) {
        currentPlayer.setIntention(new Position(col, row));
    }

    public void SetInternetRoomInfo(String aName,String bName,boolean AIsBlack){
        if(player1.getName().equals(aName)^AIsBlack){
            player1.setPieceType(2);
            player2.setPieceType(1);
            currentPlayer = player2;
        }
        else{
            player1.setPieceType(1);
            player2.setPieceType(2);
            currentPlayer = player1;
        }
        if(player1.getName().equals(aName)) player2.setName(bName);
        else player2.setName(aName);
        if (player1.getPieceType() == 1) gameHistory.setColor("执黑");//在数据库中插入棋子的颜色
        else gameHistory.setColor("执白");
        gameHistory.name = player1.getName() + " vs " + player2.getName();
        helper.SetName(currentPlayer.getName());
    }

    public void PassIntention(Position intention) {
        currentPlayer.setIntention(intention);
    }

    /**
     * 随机分配玩家的棋子颜色，黑棋先手
     */
    private void assignRandomColors() {
        // 随机分配玩家的棋子颜色的逻辑, 1为黑棋, 2为白棋
        //黑子先手
        if(type == GameType.PlayerVsInternet){ currentPlayer = player2;return;}
        Random random = new Random();
        if (random.nextBoolean()) {
            player1.setPieceType(2);
            player2.setPieceType(1);
            currentPlayer = player2;
            Log.d("黑子先行", "player1");

        } else {
            player1.setPieceType(2);
            player2.setPieceType(1);
            currentPlayer = player2;
            Log.d("黑子先行", "player2");
        }
    }

    /**
     * 切换落子玩家
     */
    private void switchPlayer() {
        turns++;
        if (currentPlayer == player1) {
            currentPlayer = player2;
            Log.d("currentPlayer", "player2");
        } else {
            currentPlayer = player1;
            Log.d("currentPlayer", "player1,该你啦！");
        }
        helper.SetName(currentPlayer.getName());
    }

    public void ContinueDetect(Position intention) {
        if (!isGameOver(intention)) {
            // 如果游戏未结束，则切换玩家
            switchPlayer();
            helper.SetTurns(getTurns());
            if (getCurrentPlayer().getPlayerType() == PlayerType.AI) {
                StartAi();
            }
        } else {
            //其实每次的回合数自增对应的是上一次落子，所以即使游戏结束了，最后一次落子后不切换玩家也要+1
            turns++;
            helper.SetTurns(getTurns());
            GameFinish();
        }
    }

    public void GameFinish() {
        gameHistory.cnt = turns;
        gameHistory.name = player1.getName() + " vs " + player2.getName();
        if (GetWinner() == null) gameHistory.result = "平局";
        else if (GetWinner() == player1) gameHistory.result = "胜";
        else gameHistory.result = "负";
        //获取当前时间
        Date date = new Date();
        @SuppressLint("SimpleDateFormat") SimpleDateFormat f = new SimpleDateFormat("MM/dd-HH:mm");
        gameHistory.DATE_FORMAT = f.format(date);
        helper.showInfoDialog(GetWinner().getName()+"胜！", "测试信息", "", (t) -> {
            context.startActivity(new Intent(context, MainActivity.class));
        }, "", t -> Restart());
        gameHistory.SubmitToSql();
        Log.d(gameHistory.name, gameHistory.toString());
        //helper.showInfoDialog(GetWinner().getName(),t-> Restart());
    }

    public boolean isGameOver(Position position) {
        int row = position.row;
        int col = position.col;
        // 判断游戏是否结束的逻辑
        // 判断是否有一方获胜
        if (board.isFiveInLine(row, col)) {
            winner = currentPlayer;
            return true;
        }
        if (board.isFull()) {
            winner = null;
            return true;
        }
        return false;
    }

    public void StartAi() {
        aiThread = new AiThread();
        aiThread.start();
    }

    private class AiThread extends Thread {
        @Override
        public void start() {
            super.start();
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            Looper.prepare();
            super.run();
            // Ai线程的逻辑
            AI ai = (AI) getCurrentPlayer();
            int[] move = ai.getBestMove(board);
            Position intention = new Position(move[1], move[0]);
            ai.game.PassIntention(intention);
            ai.game.RunATurn();
            Looper.loop();
        }
    }

    public Player GetWinner() {
        return winner;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public int getTurns() {
        return turns;
    }

    // 其他方法和逻辑
}

