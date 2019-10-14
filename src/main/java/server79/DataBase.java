package server79;

import java.sql.*;

class DataBase {
   private Connection connection = null;
   private Statement statement = null;
   private String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
   private String DB_URL = "jdbc:mysql://39.97.171.14:3306/webrtclive?"
           +"user=root&password=123abc&useUnicode=true&characterEncoding=UTF-8";//&autoReconnect=true

   DataBase(){
       this.initial();
   }
   protected Connection getConnection() {
       return connection;
   }

   private void initial(){
       try {
           Class.forName(JDBC_DRIVER);
       } catch (ClassNotFoundException e) {
           e.printStackTrace();
       }

       //  System.out.println("连接数据库...");
       try {
           connection = DriverManager.getConnection(DB_URL);
       } catch (SQLException e) {
           e.printStackTrace();
       }

       //  System.out.println("实例化statement对象...");
       try {
           statement = connection.createStatement();
       } catch (SQLException e) {
           e.printStackTrace();
       }
   }
    protected int getUserAdd(String user_id){

        int userAdd=0;
        try{
            String sql;

            sql = "SELECT user_add From user_ip where user_id='"+user_id+"'";//user_id:deviceID user_add:pdpAdd
            ResultSet rs = statement.executeQuery(sql);
            //  System.out.println(rs.toString());
            while ((rs.next())){
                // String user_id = rs.getString("user_id");
                userAdd = rs.getInt("user_add");
                System.out.println("usrAdd:"+userAdd);
            }

            rs.close();
           // System.out.println(connection.isValid(2));
          //  connection.close();

        }catch (SQLException se){
            se.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        }
        return userAdd;

    }
    protected boolean containPdpAdd(int pdpAddInt) {
        boolean contain = false;
        try {

            String sql;

            sql = "SELECT user_add From user_ip ";
            ResultSet rs = statement.executeQuery(sql);
            //  System.out.println(rs.toString());
            while ((rs.next())) {
                // String user_id = rs.getString("user_id");
                int add = rs.getInt("user_add");
                if(add==pdpAddInt){
                 contain = true;
                }
            }

            rs.close();

        } catch (SQLException se) {
            se.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return contain;
    }

}