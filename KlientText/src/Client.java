import java.net.*;
import java.io.*;
import java.sql.Timestamp;
import java.util.Hashtable;
import java.util.Scanner;

public class Client implements Runnable {

    private int idsesji;
    private DatagramSocket socket;
    private static boolean cond = true;
    private boolean ingame = false;
    private InetAddress ip;
    private int port;
    private Listener listener;
    private Timestamp timestamp = new Timestamp(System.currentTimeMillis());

    private Client(String inet, int port) {
        try {
            this.port = port;
            ip = InetAddress.getByName(inet);
            System.out.println("Oczekiwanie na połączenie...");
            socket = new DatagramSocket();
            socket.send(generujPakiet("connect", "chce", 0, 0));
            DatagramPacket pakiet = new DatagramPacket(new byte[256], 256);
            socket.setSoTimeout(5000);
            socket.receive(pakiet);
            decode(new String(pakiet.getData()));
            socket.setSoTimeout(100);
            listener = new Listener(this,socket);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            socket = null;
        }

    }

    private void execute(String operacja, String odpowiedz, int liczba, int czas, int id) {
        if(!operacja.equals("response") && !odpowiedz.equals("ACK")) {
            send("response", "ACK", idsesji, 0);
            try{
                Thread.sleep(100);
            }catch (InterruptedException e){
                System.out.println(e.getMessage());
            }
        }
        switch (operacja) {
            case "start":
                if (odpowiedz.equals("start") && !ingame) {
                    System.out.println("Start!");
                    ingame = true;
                }
                break;
            case "notify":
                if (ingame) {
                    if (odpowiedz.equals("mala")) {
                        System.out.println("Liczba jest za mała");
                    }
                    if (odpowiedz.equals("czas")) {
                        System.out.println("Pozostało " + czas + " sekund");
                    }
                    if (odpowiedz.equals("duza")) {
                        System.out.println("Liczba jest za duża");
                    }
                }
                break;
            case "end":
                if (odpowiedz.equals("przegrana")) {
                    System.out.println("Wygrał inny gracz, poprawna liczba to: " + liczba);
                }
                if (odpowiedz.equals("wygrana")) {
                    System.out.println("Wygrałeś!");
                }
                if (odpowiedz.equals("koniecCzasu")) {
                    System.out.println("Czas się skończył.");
                }
                try {
                    Thread.sleep(idsesji);
                }
                catch(InterruptedException e){
                    System.err.println(e.getMessage());
                }
                send("end", "zakonczPol", idsesji, 0);
                listener.warunek = false;
                ingame = cond = false;
                break;
            case "answer":
                if (odpowiedz.equals("accept")) {
                    idsesji = id;
                    System.out.println("Połączono z serwerem. Otrzymano ID: "+id);
                }
                break;
            case "response":
                return;
            default:
                System.out.println("Otrzymano nieznany komunikat");
        }
    }

    void decode(String data) {
        int liczba, czas, id;
        String[] options = data.split("<<");

        Hashtable<String, String> optionsSplit = new Hashtable<>();

        for (String elem : options) {
            String[] temp = elem.split("[?]");
            if(temp.length == 2)
                optionsSplit.put(temp[0], temp[1]);
        }

        liczba = Integer.parseInt(optionsSplit.get("LI"));
        czas = Integer.parseInt(optionsSplit.get("CZ"));
        id = Integer.parseInt(optionsSplit.get("ID"));

        if (id == idsesji || idsesji == 0) {
            execute(optionsSplit.get("OP"), optionsSplit.get("OD"), liczba, czas, id);
        } else {
            System.out.println("Odebrano niepoprawny komunikat od serwera");
        }

    }

    private DatagramPacket generujPakiet(String operacja, String odpowiedz, int id, int liczba) {

        byte[] buff = new byte[256];

        DatagramPacket pakiet = new DatagramPacket(buff, 256);

        String komunikat = "";

        komunikat += "OP?" + operacja + "<<";
        komunikat += "OD?" + odpowiedz + "<<";
        komunikat += "ID?" + id + "<<";
        komunikat += "LI?" + liczba + "<<";
        komunikat += "CZ?" + 0 + "<<";
        komunikat += "TS?" + timestamp.getTime()+"<<";
        //komunikat += "\0";

        pakiet.setData(komunikat.getBytes());

        pakiet.setAddress(ip);
        pakiet.setPort(port);

        return pakiet;
    }

    private void send(String operacja, String odpowiedz, int id, int liczba) {
        try {
            DatagramPacket pakiet = generujPakiet(operacja,odpowiedz,id,liczba);
            socket.send(pakiet);
            if(!(operacja.equals("response") && odpowiedz.equals("ACK"))){
                listener.accepted = false;
            }
        } catch (IOException r) {
            System.err.println(r.getMessage());
        }
    }

    void setCondition(){
        cond = false;
    }

    public static void main(String[] args) {
        if (args.length == 2) {
            Client client = new Client(args[0], Integer.parseInt(args[1]));
            if (client.socket != null) {
                new Thread(client).start();
            } else {
                System.out.println("Nie można było połączyć z serwerem");
                cond = false;
            }
        }
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        int liczba;
        Thread listen = new Thread(listener);
        listen.start();
        while (cond) {
            try {
                if (System.in.available() > 0) {
                    liczba = scanner.nextInt();
                    send("notify", "liczba", idsesji, liczba);
                }
            } catch (Throwable e) {
                scanner.next();
            }
        }
        socket.close();
    }
}
