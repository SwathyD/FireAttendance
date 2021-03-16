package com.example.fireattendance;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

public class algo{

    static boolean isInside(int RSSI,int target_battery_mah,int target_battery_percent,int detector_battery_mah,int detector_battery_percent,int target_manufacturer,int detector_manufacturer){
        return (RSSI>-50)?true:false;

        //Put Model here
//        if(RSSI <= -72.50){
//            if(target_battery_percent <= 24.00){
//                return true;
//            }
//            else{
//                if(detector_manufacturer <= 1.50){
//                    if(RSSI <= -79.00){
//                        return false;
//                    }
//                    else{
//                        return true;
//                    }
//                }
//                else{
//                    return false;
//                }
//            }
//        }
//        else{
//            if(RSSI <= -66.00){
//                if(detector_battery_percent <= 77.00){
//                    return false;
//                }
//                else{
//                    if(RSSI <= -68.50){
//                        if(detector_battery_percent <= 86.50){
//                            return true;
//                        }
//                        else{
//                            if(RSSI <= -70.50){
//                                return false;
//                            }
//                            else{
//                                return true;
//                            }
//                        }
//                    }
//                    else{
//                        if(detector_battery_percent <= 86.50){
//                            if(detector_battery_mah <= 3025.50){
//                                return true;
//                            }
//                            else{
//                                return false;
//                            }
//                        }
//                        else{
//                            return true;
//                        }
//                    }
//                }
//            }
//            else{
//                if(detector_battery_percent <= 76.50){
//                    if(detector_battery_percent <= 36.50){
//                        return true;
//                    }
//                    else{
//                        if(RSSI <= -47.50){
//                            return false;
//                        }
//                        else{
//                            return true;
//                        }
//                    }
//                }
//                else{
//                    return true;
//                }
//            }
//        }
    }
    public static void main(String[] args) {


    }

    public static ArrayList<Algo_Student_Record> validate(ArrayList<BluetoothEntry> record){
        Map<String, Pair> nodes = new HashMap();
        Map<String, String> association = new HashMap<>();
        for(int i=0; i<record.size(); i++){
            if(!nodes.containsKey(record.get(i).recorder))
                nodes.put(record.get(i).recorder, new Pair(true,-999));
            if(nodes.get(record.get(i).recorder).isInside==true){
                boolean inside = isInside(record.get(i).rssi,record.get(i).target_battery_mah,record.get(i).target_battery_percent,record.get(i).detector_battery_mah,record.get(i).detector_battery_percent,1,1);
                association.put(record.get(i).recordee, record.get(i).recorder);
                if(!nodes.containsKey(record.get(i).recordee))  nodes.put(record.get(i).recordee, new Pair(inside, record.get(i).rssi));
                else if(record.get(i).rssi > nodes.get(record.get(i).recordee).max_rssi){
                    nodes.put(record.get(i).recordee, new Pair(inside, record.get(i).rssi));
                }
            }else{
                nodes.put(record.get(i).recordee, new Pair(true,record.get(i).rssi));
            }
            for(String key : nodes.keySet()){
                if(nodes.get(key).isInside == false){
                    if(nodes.get(association.get(key)).isInside == false)
                        nodes.put(key, new Pair(true,nodes.get(key).max_rssi));
                }
            }
            Log.i("INFO NODES",nodes.toString());
        }
        ArrayList<Algo_Student_Record> algo_student_records = new ArrayList<Algo_Student_Record>();
        for(String key : nodes.keySet()){
            algo_student_records.add(new Algo_Student_Record(key,nodes.get(key).isInside));
        }

        return algo_student_records;
    }
}

class Pair{
    boolean isInside;
    int max_rssi;

    Pair(boolean isInside, int max_rssi){
        this.isInside = isInside;
        this.max_rssi = max_rssi;
    }

    public String toString(){
        return "("+isInside+" , "+max_rssi+")";
    }
}

class BluetoothEntry{
    String recorder, recordee;
    int rssi;
    int target_battery_mah;//1
    int target_battery_percent;//2
    int detector_battery_mah;//3
    int detector_battery_percent;//4
    int target_manufacturer;//5
    int detector_manufacturer;//6

    BluetoothEntry(String rd, String re, int r,int target_battery_mah,int target_battery_percent,int detector_battery_mah,int detector_battery_percent){
        recorder = rd;
        recordee = re;
        rssi = r;
        this.target_battery_mah= target_battery_mah;
        this.target_battery_percent = target_battery_percent;
        this.detector_battery_mah = detector_battery_mah;
        this.detector_battery_percent = detector_battery_percent;
        this.target_manufacturer = 1;
        this.detector_manufacturer = 1;
    }
}


class Algo_Student_Record{
    String uid;
    boolean isInside;

    public Algo_Student_Record(String uid, boolean isInside) {
        this.uid = uid;
        this.isInside = isInside;
    }
}