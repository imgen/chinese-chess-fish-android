package com.zfdang.chess.openbook;


import com.zfdang.chess.utils.HttpUtils;
import com.zfdang.chess.utils.StringUtils;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class CloudOpenBook implements OpenBook {

    private final static String URL = "http://www.chessdb.cn/chessdb.php";
    private final int cloudTimeout = 1000;

    @Override
    public List<BookData> get(char[][] board, boolean redGo)  {
        return null;
    }

    @Override
    public List<BookData> get(String fenCode, boolean onlyFinalPhase) {
        List<BookData> list = new ArrayList<>();
        try {
            String content = "action=queryall&board=" + URLEncoder.encode(fenCode, "UTF-8");
            String result = HttpUtils.sendByGet(URL, content, cloudTimeout);
            System.out.println(result);

            if (StringUtils.isNotEmpty(result) && result.contains("move")) {

                String[] datas = result.split("\\|");
                for (String data : datas) {

                    BookData bd = new BookData();
                    bd.setSource("云库");
                    String[] items = data.split(",");
                    boolean finalPhase = false;
                    for (String item : items) {
                        String[] kvs = item.split(":");
                        if ("move".equals(kvs[0])) {
                            bd.setMove(kvs[1]);
                        } else if ("score".equals(kvs[0])) {
                            bd.setScore(Integer.parseInt(kvs[1]));
                        } else if ("winrate".equals(kvs[0])) {
                            bd.setWinRate(Double.parseDouble(kvs[1]));
                        } else if ("note".equals(kvs[0])) {
                            bd.setNote(kvs[1]);
                            if (kvs[1].contains("W") || kvs[1].contains("D") || kvs[1].contains("L")) {
                                finalPhase = true;
                            }
                        }
                    }
                    if (!(onlyFinalPhase || finalPhase)) {
                        list.add(bd);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    @Override
    public void close() {

    }

}