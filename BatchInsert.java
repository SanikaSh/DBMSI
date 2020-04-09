import global.*;
import index.*;
import BigT.*;
import java.io.*;
import java.lang.*;
import diskmgr.*;
import bufmgr.*;
import btree.*; 
import catalog.*;
import iterator.*;

public class BatchInsert implements GlobalConst {

    public static void main(String[] args) {

        //Parsing arguments:
        if (args.length != 4 || args[0] == "-h")
        {
            System.out.println("Enter correct arguments: \nbatchinsert DATAFILENAME TYPE BIGTABLENAME NUMBUFF");
            return;
        }

        String dataFileName = args[0], bigtableName = args[2];
        int databaseType = Integer.parseInt(args[1]);
        int numbuffs = Integer.parseInt(args[3]);
        String dbpath = "/tmp/"+System.getProperty("user.name")+"database"; 
        SystemDefs sysdef = new SystemDefs( dbpath, 20000, numbuffs, "Clock");

        String[] streamBTNames = new String[6];
        for(int i=0; i<5; i++) streamBTNames[i] = "old_"+(i+1);

        //Reading input csv file:
        try {
            BufferedReader br = new BufferedReader(new FileReader(dataFileName));

            String line = br.readLine();

            bigT tempbt = new bigT(null);
            streamBTNames[5] = tempbt.get_fileName();

            // Put the new data into temporary bigT
            while (line != null) {

                Map m = new Map();
                String[ ] attributes = line.split(",");
                m.setValue(attributes[2]);
                m = new Map(m.size());
                m.setRowLabel(attributes[0]);
                m.setColumnLabel(attributes[1]);
                m.setTimeStamp(Integer.parseInt(attributes[3]));
                m.setValue(attributes[2]);

                try {
                    tempbt.insertMap(m.returnMapByteArray());
                    System.out.println("Added map timestamp to temporary: " + attributes[3]);
                }catch (Exception e) {
                    System.err.println("*** error in bigT.insertMap() ***");
                    e.printStackTrace();
                }


                line = br.readLine();
            }

            // Ensure that all bigTables and their indexes are there before attempting renaming
            for(int i=1; i<=5; i++){
                bigT b = new bigT(bigtableName +"_" +i);
                b.createIndex();
            } 

            // Rename all the bigTable file entries along with indexes to something else
            for(int i=0; i<5; i++){
                SystemDefs.JavabaseDB.rename_file_entry(bigtableName +"_" +(i+1), streamBTNames[i] );
                if(i!=0)
                SystemDefs.JavabaseDB.rename_file_entry(bigtableName +"_" +(i+1)+"_index", streamBTNames[i]+"_index" );
            }

            // Create new Big Tables
            //System.out.println("Creating new big tables");
            bigT[] newBTs = new bigT[5];
            for(int i=0; i<5; i++){
                newBTs[i] = new bigT(bigtableName +"_" +(i+1));
                newBTs[i].createIndex();
            }

            // Initialize the Big Stream
            BigStream stream = new BigStream(streamBTNames, 6, "*", "*", "*");

            // Delete the old big tables & their indexes
            for(int i=0; i<5; i++) {
                bigT b = new bigT(streamBTNames[i]);
                b.deletebigT(); // Handles deletion of index as well
            }
            
            // Distribute the maps to respective big tables
            Map prevMap = null;
            Map rMap;
            int prevCount = 0;
            while(true){
                if((rMap   = stream.getNext()) == null){
                    break;
                }
                if(prevMap!=null && prevMap.getRowLabel().equals(rMap.getRowLabel()) && prevMap.getColumnLabel().equals(rMap.getColumnLabel()) ){
                    prevCount++;
                } else {
                    prevCount = 1;
                    prevMap = new Map(rMap);
                }
                if(prevCount<=3){
                    System.out.println("Added Map TS from temp to new: " + rMap.getTimeStamp());
                    if(stream.outInd==5) newBTs[databaseType-1].insertMap(rMap.returnMapByteArray());
                    else newBTs[stream.outInd].insertMap(rMap.returnMapByteArray());
                }
            }
            stream.close();
            tempbt.deletebigT();

            // Reorder bigT type 3,4,5 bigTs based on the expected storage order
            for(int i=3; i<=5; i++){
                SystemDefs.JavabaseDB.rename_file_entry(bigtableName +"_" +i, "old_"+i );
                SystemDefs.JavabaseDB.rename_file_entry(bigtableName +"_" +i+"_index", "old_"+i+"_index" );

                int ordertype = 1;
                if (i == 2 | i == 5)
                    ordertype = 1;
                else if(i == 3 | i == 4)
                    ordertype = 2;
                bigT b = new bigT("old_"+i);
                Stream reorderStream = new Stream(b, ordertype, "*", "*", "*");

                b.deletebigT();

                b = new bigT(bigtableName +"_" +i);
                b.createIndex();
                while(true){

                    Map out = reorderStream.getNext();
                    if(out == null) break;

                    b.insertMap(out.getMapByteArray());
                }
                reorderStream.close();
            }


            sysdef.close();

        }catch (IOException e) {
            e.printStackTrace();
        }catch (InvalidMapSizeException e){
            System.err.println("*** InvalidMapSize ***");
            e.printStackTrace();
        }catch(Exception e){
            System.err.println("*** error ***");
            e.printStackTrace();
        }

}

}
