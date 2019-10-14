package server79;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class NonRegImpl implements NonReg {
    private DataBase dataBase = NonRegHandler.dataBase;
    private ByteBuf buf;
    NonRegImpl(DatagramPacket msg){
        msg.retain();
        buf = msg.content();
    }

    /**ID解析: Type = 0x01
     *用户发送0xD6	0x01	ID长度（8bit）	ID（不定长）
     *服务器返回0xD6	0x01	错误码（8bit）	PDP地址（32bit）*/
    @Override
    public void parseId(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        //  System.out.println("ID解析");
        byte lengthOfId = buf.getByte(2);
        byte[] ID = new byte[lengthOfId];
        buf.getBytes(3, ID, 0, lengthOfId);
        String IDString = new String(ID, "US-ASCII");//deviceID
        System.out.println(IDString + "id translate");
        byte[] echo = new byte[7];
        echo[0] = (byte) 0xD6;
        echo[1] = (byte) 0x01;
        int pdpAddInt;
    /** 从数据库取出对应pdpAdd*/
        if (dataBase.getConnection().isValid(2)) {
            pdpAddInt = dataBase.getUserAdd(IDString);//pdpAdd
        } else {
            dataBase = new DataBase();
            pdpAddInt = dataBase.getUserAdd(IDString);
        }
        if (pdpAddInt != 0) {
            echo[2] = (byte) 0;
            byte[] pdpAddByte = DataChange.IntToBytes(pdpAddInt);
            System.arraycopy(pdpAddByte, 0, echo, 3, 4);
        } else {
            echo[2] = (byte) -1;
        }

        /**从数据库查询ID对应PDP地址*/
        ctx.writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer(echo), msg.sender()));
    }
    /**
     *查询注册信息：Type = 0x02   0xD6	0x02	PDP地址（32bit）
     * 服务器返回 0xD6	0x02	错误码（8bit）	PDP端口（8bit）	……*/
    @Override
    public void queryRegInfo(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        byte[] pdpAddByte = new byte[4];
        buf.getBytes(2, pdpAddByte, 0, 4);
        int pdpAddInt = DataChange.bytes2Int(pdpAddByte);
        System.out.println(pdpAddInt + " apply for port");
        if (SharedTranMap.pdpPortMap.containsKey(pdpAddInt)) {
            ArrayList<Byte> portList = (ArrayList<Byte>) SharedTranMap.pdpPortMap.get(pdpAddInt);
            int numOfPort = portList.size();
            byte[] echo = new byte[3 + numOfPort];
            echo[0] = (byte) 0xD6;
            echo[1] = (byte) 0x02;
            echo[2] = (byte) 0;
            for (int i = 0; i < numOfPort; i++) {
                echo[3 + i] = portList.get(i);
                // System.out.println("已使用端口"+portList.get(i));
            }
            ctx.writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer(echo), msg.sender()));
        } else {
            // System.out.println("未注册");
            byte[] echo = new byte[]{(byte) 0xD6, (byte) 0x02, (byte) -1};
            ctx.writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer(echo), msg.sender()));
        }
    }

    /**注册地址：Type = 0x03
     *用户发送0xD6	0x03	PDP地址（32bit）	PDP端口（8bit）
     *服务器返回0xD6	0x03	错误码（8bit）	IP端口（16bit）
         * */
    @Override
    public void register(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        byte[] pdpAddByte = new byte[4];
        buf.getBytes(2, pdpAddByte, 0, 4);
        byte pdpPort = buf.getByte(6);
        int pdpAddInt = DataChange.bytes2Int(pdpAddByte);
        System.out.println(pdpAddInt + "apply for register");
        short ipPort;
        byte[] echo = new byte[5];
        echo[0] = (byte) 0xD6;
        echo[1] = (byte) 0x03;

        /**新建对象*/
        Pdp pdp;
        PdpSocket pdpSocket = new PdpSocket(pdpAddInt, pdpPort);
        /**
         * 由pdpPortMap是否存在对应pdp判断*/
        if (SharedTranMap.pdpPortMap.containsValue(pdpAddInt, pdpPort)) {
            // System.out.print("重复注册");
            pdp = SharedTranMap.objectWithSocket.get(pdpSocket);
            ipPort = pdp.getIpPort();
            // System.out.println("分配ip端口为：" + echoPort);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd :hh:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            pdp.setLogInTime(dateFormat.format(new Date())+"repeat register");
            pdp.setLogOffTime("");
            echo[2] = (byte) 0;//成功
            echo[3] = (byte) ((ipPort >> 8) & 0xff);//port高8位
            echo[4] = (byte) (ipPort & 0xff);//port低八位
            pdp.getTimer().cancel(false);
            ScheduledFuture timer = ctx.executor().schedule(new Remover(pdp), 10, TimeUnit.SECONDS);
            pdp.setTimer(timer);
//            pdp.setState(pdp.getState() + 1);
//            ctx.executor().schedule(new Remover(pdp), 10, TimeUnit.SECONDS);
        } else {
            if (!dataBase.getConnection().isValid(2)) {
                dataBase = new DataBase();
            }
            if (dataBase.containPdpAdd(pdpAddInt)) {
                //  System.out.println("初次注册");
                pdp = new Pdp(pdpSocket);

                /**将add及对应port存入map*/
                SharedTranMap.pdpPortMap.put(pdpAddInt, pdpPort);
                /**存入对象及其socket(Socket,Object)*/
                SharedTranMap.objectWithSocket.put(pdpSocket, pdp);
                /**计算*/
                long rateB = ServerTest.b.getSpeedOfPort();
                long rateC = ServerTest.c.getSpeedOfPort();
                long rateD = ServerTest.d.getSpeedOfPort();

                long rate = Math.min(Math.min(rateB, rateC), rateD);
                if (rate == rateB) {
                    ipPort = (short) 5467;
                } else if (rate == rateC) {
                    ipPort = (short) 5468;
                } else {
                    ipPort = (short) 5469;
                }
                pdp.setCtx(ctx);
                pdp.setIpAdd(msg.sender());
                pdp.setIpPort(ipPort);
                Date date = new Date();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
                pdp.setLogInTime(dateFormat.format(date)+"first register");
                pdp.setLogOffTime("");
        //        pdp.setState(1);
                echo[2] = (byte) 0;//成功
                echo[3] = (byte) ((ipPort >> 8) & 0xff);//port高8位
                echo[4] = (byte) (ipPort & 0xff);//port低八位
                ScheduledFuture timer = ctx.executor().schedule(new Remover(pdp), 10, TimeUnit.SECONDS);
                pdp.setTimer(timer);
          //      ctx.executor().schedule(new Remover(pdp), 10, TimeUnit.SECONDS);
                ScheduledExecutorService service = new ScheduledThreadPoolExecutor(1);
                ScheduledFuture calSpeedFuture = service.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
//                        if(pdp!=null) {
                        pdp.setSpeedOfDatagram(pdp.getTestOfSpeed() / 6);
                        pdp.setTestOfSpeed(0);
//                        }
                    }
                }, 0, 6, TimeUnit.SECONDS);
                pdp.setCalSpeedFuture(calSpeedFuture);
            } else {
                /**错误码*/
                echo[2] = (byte) -1;
            }
        }
        ctx.writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer(echo), msg.sender()));
    }
    /**
     * 更新IP地址 = 0x05
     * 用户发送 0x55	0x05	源地址（40bit）*/
    @Override
    public void updateIp(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        byte[] pdpAddByte = new byte[4];
        buf.getBytes(2, pdpAddByte, 0, 4);
        int pdpAddInt = DataChange.bytes2Int(pdpAddByte);
        byte pdpPort = buf.getByte(6);
        PdpSocket pdpSocket = new PdpSocket(pdpAddInt, pdpPort);
        if (SharedTranMap.pdpPortMap.containsValue(pdpAddInt, pdpPort)) {
            System.out.println(pdpAddInt + "update IP");
            Pdp pdp = SharedTranMap.objectWithSocket.get(pdpSocket);
            //重启定时器
            pdp.getTimer().cancel(false);
            ScheduledFuture timer = ctx.executor().schedule(new Remover(pdp), 10, TimeUnit.SECONDS);
            pdp.setTimer(timer);
            pdp.setIpAdd(msg.sender());
            pdp.setCtx(ctx);
        } else {
            System.out.println("address error when updating ip");
        }
    }
    /**注销 = 0x04
     *用户发送0x55	0x04	源地址（40bit）*/
    @Override
    public void cancel(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        byte[] pdp_Address = new byte[4];
        buf.getBytes(2, pdp_Address, 0, 4);

        int pdpAddInt = DataChange.bytes2Int(pdp_Address);


        byte[] pdpSocketByte = new byte[5];
        buf.getBytes(2, pdpSocketByte, 0, 5);
        byte pdpPort = buf.getByte(6);
        PdpSocket pdpSocket = new PdpSocket(pdpAddInt, pdpPort);
        /**
         * 注销只在pdpPortMap中进行删除*/
        if (SharedTranMap.pdpPortMap.containsValue(pdpAddInt, pdpPort)) {
            System.out.println(pdpAddInt + "cancel");
            SharedTranMap.pdpPortMap.remove(pdpAddInt, pdpPort);
            Pdp pdp = SharedTranMap.objectWithSocket.get(pdpSocket);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
         //   pdp.setState(-1);
            pdp.getTimer().cancel(false);
            pdp.setLogOffTime(dateFormat.format(new Date()));
            pdp.getCalSpeedFuture().cancel(false);
        }
    }
}