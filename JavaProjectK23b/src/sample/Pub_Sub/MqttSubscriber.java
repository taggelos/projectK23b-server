package sample.Pub_Sub;

/**
 * Created by Maria on 28/12/2016.
 */

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.sql.*;


import java.sql.Date;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import static sample.Controller.x_threshold;
import static sample.Controller.y_threshold;
import static sample.Controller.z_threshold;
import static sample.Controller.max_light_threshold;
import static sample.Controller.min_light_threshold;



public class MqttSubscriber implements MqttCallback {

    private static MqttPublisher publisher;
    private static MqttPublisher publisher2;



    public static void main() {

        String topic="#";
        int qos = 2;
        String broker = "tcp://localhost:1883";
        String clientId = "Μyclientid2";
        MemoryPersistence persistence = new MemoryPersistence();

        try {
            MqttAsyncClient sampleClient = new MqttAsyncClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            sampleClient.setCallback(new MqttSubscriber());
            System.out.println("Connecting to broker: " + broker);
            sampleClient.connect(connOpts);
            System.out.println("Connected");
            Thread.sleep(1000);
            sampleClient.subscribe(topic, qos);
            System.out.println("Subscribed");
        } catch (Exception me) {
            if (me instanceof MqttException) {
                System.out.println("reason " + ((MqttException) me).getReasonCode());
            }
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("excep " + me);
            me.printStackTrace();
        }

    }
    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("connection lost");

    }

//-------------------THREAD FOR EACH TOPIC THAT JUST ARRIVED IN ORDER TO BE MANAGED------------------------


    private class ManagerThread implements Runnable {
        String[] arr;
        public ManagerThread(String topic) {

            arr = topic.split("/");


        }

        public void inserInMyDataBase(String macAddress ,String latitude ,String longtitude ,String sensorType ,String sensor_Value , String  date ) {


            try {
                Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb?autoReconnect=true&useSSL=false", "root", "MyNewPass");
                String SQL = "INSERT INTO  blind_light_data(user_id ,location_latitude , location_longitude , sensorType , sensorValue , date_time)  VALUES(? , ? , ? , ? , ? , ?)";


                DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss" );
                java.util.Date ends_date = df.parse(date);
                Timestamp mytmp = new java.sql.Timestamp(ends_date.getTime());


                PreparedStatement prepared_state= connection.prepareStatement(SQL);
                prepared_state.setString(1 , macAddress );
                prepared_state.setFloat(2 , Float.parseFloat(latitude));
                prepared_state.setFloat(3 , Float.parseFloat(longtitude));
                prepared_state.setString(4 , sensorType );
                prepared_state.setString(5 , sensor_Value );
                prepared_state.setTimestamp(6 , mytmp );


                System.out.println(prepared_state+"----fwefwf-------");
                prepared_state.executeUpdate();


            }
            catch ( SQLException err ) {
                System.out.println( err.getMessage( ) );
            } catch (ParseException e) {
                e.printStackTrace();
            }

        }

        @Override
        public  void run() {
            int cntr = arr.length;
            if (cntr == 6) {
                Boolean possible_crash = false;
                String macAddress = arr[0];
                String sensorType = arr[1];
                String sensor_Value = arr[2];
                String date = arr[3];
                String latitude = arr[4];
                String longtitude = arr[5];

                if (sensorType.equals("Accelerometer Sensor")) {

                    String[] accelero_values = sensor_Value.split(",");

                    System.out.println(macAddress);
                    System.out.println(x_threshold);
                    if ((Double.parseDouble(accelero_values[0]) >= x_threshold) || (Double.parseDouble(accelero_values[1]) >= y_threshold) || (Double.parseDouble(accelero_values[2]) >= z_threshold)) {
                        possible_crash = true;

                    }

                } else if (sensorType.equals("Light Sensor")) {

                    if (Double.parseDouble(sensor_Value) > max_light_threshold) {
                        possible_crash = true;

                    }
                    if (Double.parseDouble(sensor_Value) < min_light_threshold) {
                        possible_crash = true;

                    }

                } else {
                    if ((Double.parseDouble(sensor_Value)) == 0) {
                        possible_crash = true;

                    }

                }
                if (possible_crash.equals(true)) {
                    inserInMyDataBase(macAddress, latitude, longtitude, sensorType, sensor_Value, date);
                    publisher = new MqttPublisher();
                    publisher.main(macAddress, "Be carefull: Possibility of crash");

                    //-----------------Confirmed Crashed-----------------

                    try {

                        final String mac = "user_id";
                        java.util.Date datee=null;


                        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "root", "MyNewPass");
                        Statement stmt = connection.createStatement( );
                        String SQL = "SELECT user_id,date_time FROM blind_light_data ";
                        ResultSet rs = stmt.executeQuery( SQL );

                        while(rs.next()){
                            String allmac  = rs.getString(mac);
                            Timestamp timestamp = rs.getTimestamp("date_time");
                            String alldates =timestamp.toString();
                            if (timestamp != null)
                                datee = new java.util.Date(timestamp.getTime());
                                if((macAddress!=allmac) &&(date==alldates)){

                                    //-----------both devices need to be informed-------------
                                    publisher = new MqttPublisher();
                                    publisher.main(macAddress,"Confirmed Crash");
                                    publisher2 = new MqttPublisher();
                                    publisher2.main(allmac ,"Confirmed Crash");

                                }


                        }



                        rs.close();
                    }
                    catch ( SQLException err ) {
                        System.out.println( err.getMessage( ) );
                    }
                }

            }
        }

    }



    public void changeTable(JTable blind_light_data, int column_index) {
        blind_light_data.getColumnModel().getColumn(column_index).setCellRenderer(new DefaultTableCellRenderer() {

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                final Component c = super.getTableCellRendererComponent(table, value,
                        isSelected, hasFocus, row, column);

                        setBackground(new Color(255, 101, 18));

                return c;
            }
        });
    }



    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        System.out.println("topic: " + topic);
        System.out.println("message: " + new String(message.getPayload()));
        ManagerThread mng_thread = new ManagerThread(topic);
        Thread tmp_thread = new Thread(mng_thread);
        tmp_thread.start();


    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        System.err.println("delivery complete");
    }


}


