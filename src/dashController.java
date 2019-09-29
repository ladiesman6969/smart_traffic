import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Paint;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ResourceBundle;

public class dashController implements Initializable {

    @FXML
    private Label monitoringSystemStatusLabel;
    @FXML
    private TextArea systemLogTextField;
    @FXML
    private Label road1CarsLabel;
    @FXML
    private Label road2CarsLabel;
    @FXML
    private Label road1SignalLabel;
    @FXML
    private Label road2SignalLabel;
    @FXML
    private ToggleButton toggleMonitorStatusButton;
    @FXML
    private Slider currentThresholdSlider;
    @FXML
    private Label currentThresholdLabel;
    @FXML
    private Button retryInitButton;
    @FXML
    private Button shutdownButton;
    @FXML
    private ImageView cam0ImageView;
    @FXML
    private ImageView cam1ImageView;

    boolean msStatus = false;

    private int POLE_1_GREEN = 21;
    private int POLE_1_YELLOW = 20;
    private int POLE_1_RED = 16;
    private int POLE_2_GREEN = 26;
    private int POLE_2_YELLOW = 6;
    private int POLE_2_RED = 13;
    private int POLE_SIGNAL_INTERVAL = 1000;

    VideoCapture cam0;
    VideoCapture cam1;
    int road1Cars = 0;
    int road2Cars = 0;
    int road1Signal = 1;
    int road2Signal = 2;

    boolean isShutdown = false;
    boolean isForceClose = false;
    double threshold = 0.5;
    Runtime r = Runtime.getRuntime();
    /*
    Road Signals :
    1 - GREEN
    2 - RED
     */
    Net model;
    String classNames[] = new String[] {"background", "person", "bicycle", "car", "motorcycle", "airplane", "bus",  "train",  "truck", "boat", "traffic light", "fire hydrant", "",  "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "", "backpack", "umbrella", "", "", "handbag", "tie", "suitcase",  "frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle", "", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut","cake", "chair", "couch", "potted plant", "bed", "", "dining table", "", "", "toilet", "", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"};

    DecimalFormat df = new DecimalFormat("0.0");
    Image camClosedImg = new Image("assets/camClosed.PNG");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        try
        {
            setLEDMode(POLE_1_GREEN,2);
            setLEDMode(POLE_1_YELLOW,2);
            setLEDMode(POLE_1_RED,2);
            setLEDMode(POLE_2_GREEN,2);
            setLEDMode(POLE_2_YELLOW,2);
            setLEDMode(POLE_2_RED,2);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        currentThresholdSlider.setValue(threshold*100);
        currentThresholdLabel.setText(threshold+"");
        currentThresholdSlider.setOnMouseReleased(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                threshold = Double.parseDouble(df.format(currentThresholdSlider.getValue()/100));
                currentThresholdLabel.setText(threshold+"");
            }
        });
        retryInitButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                new Thread(new Task<Void>() {
                    @Override
                    protected Void call() {
                        runSetup();
                        return null;
                    }
                }).start();
            }
        });
        shutdownButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                isShutdown = true;
            }
        });

        new Thread(new Task<Void>() {
            @Override
            protected Void call() {
                try {
                    runSetup();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                return null;
            }
        }).start();
    }


    void setLEDMode(int gpio, int inOrOut) throws Exception
    {
        if(inOrOut == 1)
        {
            r.exec("gpio -g mode "+gpio+" in");
        }
        else if(inOrOut == 2)
        {
            r.exec("gpio -g mode "+gpio+" out");
        }
    }

    void writeLEDStatus(int gpio, boolean isHigh) throws Exception
    {
        if(isHigh)
        {
            r.exec("gpio -g write "+gpio+" 1");
        }
        else
        {
            r.exec("gpio -g write "+gpio+" 0");
        }
    }

    @FXML
    private void toggleMSStatus()
    {
        if(msStatus)
        {
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    monitoringSystemStatusLabel.setText("OFF");
                    monitoringSystemStatusLabel.setTextFill(Paint.valueOf("#ff0000"));
                }
            });

            msStatus = false;
        }
        else
        {
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    monitoringSystemStatusLabel.setText("ON");
                    monitoringSystemStatusLabel.setTextFill(Paint.valueOf("#45bb00"));
                }
            });
            msStatus = true;
        }
    }

    void runSetup()
    {
        try
        {
            log("Testing LEDs (Street Pole Lights) ...");

            writeLEDStatus(POLE_1_GREEN,true);
            writeLEDStatus(POLE_1_YELLOW,true);
            writeLEDStatus(POLE_1_RED,true);
            writeLEDStatus(POLE_2_GREEN,true);
            writeLEDStatus(POLE_2_YELLOW,true);
            writeLEDStatus(POLE_2_RED,true);

            Thread.sleep(1000);

            writeLEDStatus(POLE_1_GREEN,false);
            writeLEDStatus(POLE_1_YELLOW,false);
            writeLEDStatus(POLE_1_RED,false);
            writeLEDStatus(POLE_2_GREEN,false);
            writeLEDStatus(POLE_2_YELLOW,false);
            writeLEDStatus(POLE_2_RED,false);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                toggleMonitorStatusButton.setDisable(true);
                retryInitButton.setDisable(true);
            }
        });
        boolean initSuccess = true;
        log("Init System ...");
        cam0 = new VideoCapture(0);
        cam1 = new VideoCapture(1);
        log("Loading TensorFlow Model ...");
        model = Dnn.readNetFromTensorflow("frozen_inference_graph.pb","dd.pbtxt");
        log("... Success!");
        log("Checking Hardware ...");
        log("Checking for Camera 0 ...");
        if(!cam0.isOpened())
        {
            initSuccess = false;
            log("Unable to Open Camera 0");
        }
        else
        {
            log("... Camera 1 Active!");
        }
        System.out.println("Checking for Camera 1 ...");
       /* if(!camera2.isOpened())
        {
            initSuccess = false;
            log("Unable to Open Camera 1");
        }*/

        if(!initSuccess)
        {
            showAlert("Error","Unable to Init Hardware. Check log and make sure everything is connected properly.", Alert.AlertType.ERROR);
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    toggleMonitorStatusButton.setDisable(true);
                    retryInitButton.setDisable(false);
                    cam0ImageView.setImage(camClosedImg);
                    cam1ImageView.setImage(camClosedImg);
                }
            });
        }
        else
        {
            new Thread(cameraTask).start();
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    toggleMonitorStatusButton.setDisable(false);
                    retryInitButton.setDisable(true);
                }
            });
        }
    }

    Task<Void> cameraTask = new Task<Void>() {
        @Override
        protected Void call() {
            try
            {
                if(road1Signal == 1)
                {
                    setRoad1Signal(2);
                    setRoad2Signal(1);
                }
                else if(road1Signal == 2)
                {
                    setRoad1Signal(1);
                    setRoad2Signal(2);
                }
                Mat cam0Frame = new Mat();
                log("Showing Camera feeds ...");
                int timeStagnant = 0;
                while(cam0.isOpened() && !isShutdown && !isForceClose)
                {
                    Thread.sleep(10);
                    cam0.read(cam0Frame);
                    showMatToImageView(cam0Frame,cam0ImageView);
                    showMatToImageView(cam0Frame,cam1ImageView);


                    if(msStatus)
                    {
                        model.setInput(Dnn.blobFromImage(cam0Frame,1,new Size(300,300)));
                        Mat out = model.forward();
                        int tmpCarsRoad1 = 0;
                        for(int i =0;i<out.size(2);i++)
                        {
                            int clID = (int) out.get(new int[] {0,0,i,1})[0];
                            double conf = out.get(new int[] {0,0,i,2})[0];
                            if(conf>0.5)
                            {
                                log("Camera 1 Frame Details : ");
                                log("Confidence : "+conf);
                                log("Class ID : "+clID+", Name : "+classNames[clID]);

                                if(clID == 3)
                                {
                                    log("CAR DETECTED");
                                    tmpCarsRoad1++;
                                }
                            }
                        }
                        road1Cars = tmpCarsRoad1;



                        if(road1Cars>road2Cars)
                        {
                            setRoad1Signal(1);
                            setRoad2Signal(2);
                            timeStagnant = 0;
                        }
                        else if(road2Cars>road1Cars)
                        {
                            setRoad1Signal(2);
                            setRoad2Signal(1);
                            timeStagnant = 0;
                        }
                        else if(road1Cars == road2Cars)
                        {
                            timeStagnant++;
                            if(timeStagnant == 150)
                            {
                                timeStagnant = 0;
                                if(road1Signal == 1)
                                {
                                    setRoad1Signal(2);
                                    setRoad2Signal(1);
                                }
                                else if(road1Signal == 2)
                                {
                                    setRoad1Signal(1);
                                    setRoad2Signal(2);
                                }
                            }
                        }
                    }
                    else
                    {
                        timeStagnant++;
                        if(timeStagnant == 150)
                        {
                            timeStagnant = 0;
                            if(road1Signal == 1)
                            {
                                setRoad1Signal(2);
                                setRoad2Signal(1);
                            }
                            else if(road1Signal == 2)
                            {
                                setRoad1Signal(1);
                                setRoad2Signal(2);
                            }
                        }
                    }

                    updateRoadCarsNoStatus();
                    System.gc();
                }

                if(isShutdown)
                {
                    cam0.release();
                    cam1.release();
                    r.exec("sudo halt");
                }
                else
                {
                    runSetup();
                }
            }
            catch (Exception e)
            {
                runSetup();
                e.printStackTrace();
            }
            return null;
        }
    };

    void setRoad1Signal(int newValue)
    {
        new Thread(new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if(road1Signal!=newValue)
                {
                    System.out.println("Changing...");
                    if(newValue == 1)
                    {
                        writeLEDStatus(POLE_1_RED,false);
                        writeLEDStatus(POLE_1_YELLOW, true);
                        Thread.sleep(POLE_SIGNAL_INTERVAL);
                        writeLEDStatus(POLE_1_YELLOW,false);
                        writeLEDStatus(POLE_1_GREEN,true);
                    }
                    else if(newValue == 2)
                    {
                        writeLEDStatus(POLE_1_GREEN,false);
                        writeLEDStatus(POLE_1_YELLOW, true);
                        Thread.sleep(POLE_SIGNAL_INTERVAL);
                        writeLEDStatus(POLE_1_YELLOW,false);
                        writeLEDStatus(POLE_1_RED,true);
                    }
                    log("CHANGED. Road 1 now "+newValue);
                    road1Signal = newValue;
                    if(road1Signal == 1)
                    {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                road1SignalLabel.setText("GREEN");
                                road1SignalLabel.setTextFill(Paint.valueOf("#45bb00"));
                            }
                        });
                    }
                    else
                    {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                road1SignalLabel.setText("RED");
                                road1SignalLabel.setTextFill(Paint.valueOf("#ff0000"));
                            }
                        });
                    }
                }
                return null;
            }
        }).start();

    }

    void setRoad2Signal(int newValue)
    {
        new Thread(new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if(road2Signal!=newValue)
                {
                    System.out.println("Changing...");
                    if(newValue == 1)
                    {
                        writeLEDStatus(POLE_2_RED,false);
                        writeLEDStatus(POLE_2_YELLOW, true);
                        Thread.sleep(POLE_SIGNAL_INTERVAL);
                        writeLEDStatus(POLE_2_YELLOW,false);
                        writeLEDStatus(POLE_2_GREEN,true);
                    }
                    else if(newValue == 2)
                    {
                        writeLEDStatus(POLE_2_GREEN,false);
                        writeLEDStatus(POLE_2_YELLOW, true);
                        Thread.sleep(POLE_SIGNAL_INTERVAL);
                        writeLEDStatus(POLE_2_YELLOW,false);
                        writeLEDStatus(POLE_2_RED,true);
                    }
                    log("CHANGED. Road 2 now "+newValue);
                    road2Signal = newValue;
                    if(road2Signal == 1)
                    {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                road2SignalLabel.setText("GREEN");
                                road2SignalLabel.setTextFill(Paint.valueOf("#45bb00"));
                            }
                        });
                    }
                    else
                    {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                road2SignalLabel.setText("RED");
                                road2SignalLabel.setTextFill(Paint.valueOf("#ff0000"));
                            }
                        });
                    }
                }
                return null;
            }
        }).start();
    }

    void updateRoadCarsNoStatus()
    {
        if(msStatus)
        {
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    road1CarsLabel.setText(road1Cars+"");
                    road2CarsLabel.setText(road2Cars+"");
                }
            });
        }
        else
        {
            road1Cars = 0;
            road2Cars = 0;
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    road1CarsLabel.setText("N/A");
                    road2CarsLabel.setText("N/A");
                }
            });
        }
    }

    void showMatToImageView(Mat frame, ImageView imgView)
    {
        try {
            MatOfByte buffer = new MatOfByte();
            Imgcodecs.imencode(".png", frame, buffer);
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    imgView.setImage(new Image(new ByteArrayInputStream(buffer.toArray())));
                }
            });
        }
        catch (Exception e)
        {
            isForceClose = true;
        }

    }

    void log(String toBeLogged)
    {
        System.out.println(toBeLogged);
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                systemLogTextField.appendText(toBeLogged+"\n");
            }
        });
    }

    void showAlert(String title, String body, Alert.AlertType type)
    {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                Alert a = new Alert(type);
                a.setHeaderText(title);
                a.setContentText(body);
                a.showAndWait();
            }
        });
    }
}
