package zombie.limgr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Scanner;

import org.ini4j.Ini;

public class Server {
	
	public enum State{
		ONLINE,
		OFFLINE,
		STARTING,
		ERROR
	}
	
	private Thread consoleThread; 
    private Process process; //java-process of the server
	private String console;
	private BufferedWriter consoleWriter;
	
	private File dir;

	public Server(int id) {
		this.id = id;
		this.dir = new File(Main.deploymentDir.getPath() + File.separator + this.id);
	}
	
	public int pid = 0;
	private int id = -1;
	
	/**
	 * 
	 * @return Server-ID the Servers :D
	 */
	public int getID() {
		return this.id;
	}
	
	/**
	 * Installation directory of this server
	 * @return
	 */
	public File getDir() {
		return this.dir;
	}
	
	/**
	 * Get the port of the server
	 * @return port
	 */
	public String getPort() {
		String s = this.getIni().get("Server", "Port");
		if(s!=null)
			return s;
		else {
			System.err.println("[Server # "+ id +"] [!] Invalid Port specification in server.ini ("+ s +")");
			return "ERROR";
		}
	}
	
	/**
	 * 
	 * @return Description of the server, "ERROR" in the event of a read error.
	 */
	public String getDesc() {
		String s = this.getIni().get("Server", "Desc");
		if(s!=null)
			return s;
		else {
			System.err.println("[Server # "+ id +"] [!] Invalid Desc specification in server.ini ("+ s +")");
			return "ERROR";
		}
	}
	
	/**
	 * 
	 * @return Number of allocated memory in MB. ("512" in case of error)
	 */
	public int getMemory() {
		String s = this.getIni().get("Server", "Memory").replace("M", "");
		try {
			return Integer.parseInt(s);
		}catch (NumberFormatException e) {
			System.err.println("[Server # "+ id +"] [!] Invalid memory specification in server.ini ("+ s +"). Use fallback: 512M");
			return 512;
		}
		
	}
	
	public Ini getIni() {
		File f = new File(this.dir.getPath() + File.separator + "server.ini");
		try {
			return new Ini(f);
		} catch (Throwable t) {
			System.err.println("[Server # "+ id +"] [!] An error occurred while reading the server.ini. ("+ f.getPath () +")");
			t.printStackTrace();
			return null;
		}
	}
	
	public Server.State getState() {
		if (process == null || !process.isAlive()) {
			return Server.State.OFFLINE;
		} else {
			return Server.State.ONLINE;
		}
	}
	
	public String getConsole() {
		return this.console;
	}
	
	public String[] getCmdline() {
		String s = "java-default";
		try {
			s = this.getIni().get("Server", "Cmdline");
			if(!s.equalsIgnoreCase("java-default"))
				return s.trim().split("\\s+");
		}catch (Exception e) {}
		System.out.println("[Server #"+id+"] Cmdline: "+s);
		return new String[] {"java", "-Xmx"+this.getMemory()+"M", "-jar", "server.jar"};
	}
	
	/**
	 * Starts the server.
	 * @return true if successful
	 */
	public boolean start() {
		if(this.process != null && process.isAlive())
			return false;
        try {
        	ProcessBuilder builder = new ProcessBuilder(this.getCmdline());
    		builder.redirectErrorStream(true);
    		builder.directory(new File(this.dir.getPath()+File.separator+"files"));
			this.process = builder.start();
			this.consoleThread = new Thread(processReader);
	        this.consoleThread.start();
	        this.consoleWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
	        Ini i = this.getIni();
	        i.put("Server", "Recently-Started", Util.curDate());
	        i.store();
	        return true;
		} catch (Throwable e) {
			e.printStackTrace();
			return false;
		}
        
	}
	
	/**
	 * Stops the server
	 * @return true if successful
	 */
	public boolean stop() {
		return this.sendCommand("stop");
	}
	
	/**
	 * Kills the Server
	 * @return
	 */
	public boolean kill() {
		if(process != null && process.isAlive()) {
			System.out.println("[Server #"+id+"] is forcibly terminated. . .\r\n");
			this.process.destroyForcibly();
			return true;
		}else return false;
	}
	
	/**
	 * Sends Commands  
	 * @param cmd
	 * @return
	 */
	public boolean sendCommand(String cmd) {
		if(consoleWriter != null) try {
			this.consoleWriter.write(cmd+"\n");
			this.consoleWriter.flush();
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} else return false;
	}
	
	private Runnable processReader = new Runnable() {
        public void run() {
        	try {
        		System.out.println("[SERVER # "+ id +"] Starting. . .");
        		consolePrintln("\n\n"+Main.lang.hashie.get("c_server_starting"));
                BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String s;
                try {
                	while ((s = br.readLine()) != null) {
                        System.out.println("[SERVER #"+id+"]: " + s);
                        consolePrintln(s);
                    }
                }catch(IOException e) {
                	System.err.println("[!] IOE");
                }
                
                process.waitFor();
                System.out.println("[SERVER # "+ id +"] Finished. ("+ process.exitValue () +")");
                consolePrintln("\n\n"+Main.lang.hashie.get("c_server_exited").replace("%s", process.exitValue()+""));
                consoleWriter.close();
                consoleWriter = null;
                process.destroy();
            } catch (Exception e) {
            	e.printStackTrace();
            }
        }
    };
	private void consolePrintln(String x) {
		this.console += x + "\n";
	}
    
	
	public File getFile(String path) {
		return new File(this.dir.getPath() + File.separator + "files" + File.separator + path);
	}
	
	/**
	 * Creates a server in the deployment directory and registers it.
	 * @param desc Server description.
	 * @param port Port on which the server should be placed.
	 * @param template Name of the template (* .jar or * .zip) based on which the server is to be created.
	 * @param memory Number of RAM to be assigned to the server (in MB)
	 * @param Should the server be started automatically when the system is started?
	 * @return ID of the created server, in the event of an error a negative INT which represents the error code.
	 */
	public static int Create(String desc, int port, String template, int memory, boolean autostart) {
		// Determine the new ID
		int id = 0;
		for(String f : Main.deploymentDir.list()) {
			try {
				int i = Integer.parseInt(f);
				if(i > id)
					id = i;
			}catch (NumberFormatException e) {
				continue;
			}
		}
		id++;
		File serverdir;
		while(true) {
			serverdir = new File(Main.deploymentDir.getPath() + File.separator + id);
			if(serverdir.exists()) {
				id++;
				continue;
			}else break;
		}
		
		//Template check.
		if(!getTemplates().contains(template)) 
			return -1;
		
		
		//Create folder.
		serverdir.mkdir();
		File serverdir_files = new File(serverdir.getPath() + File.separator + "files");
		serverdir_files.mkdir();
		File serverdir_backups = new File(serverdir.getPath() + File.separator + "backups");
		serverdir_backups.mkdir();
		
		//Create server.ini
		try {
			File inifile = new File(serverdir.getPath() + File.separator + "server.ini");
			if(!inifile.createNewFile())
				return -2;
			FileOutputStream f = new FileOutputStream(inifile);
			String defaultConfig = 
					"[Server]\n" + 
					"Desc="+desc.replace("\n", " ")+"\n" +
					"Memory="+memory+"M\n" +
					"Port="+port+"\n" +
					"Autostart="+Boolean.toString(autostart)+"\n" +
					"Cmdline=java-default\n" +
					"Time-Created="+Util.curDate()+"\n" +
					"\n";
			f.write(defaultConfig.getBytes("UTF-8"));
			f.flush();
			f.close();
		} catch (Throwable e) {
			e.printStackTrace();
			return -2;
		}
		
		//Copy template.
		File f_template = new File("templates" + File.separator + template);
		if(f_template.getName().endsWith(".zip") || f_template.getName().endsWith(".ZIP")) {
			try {
				File dest = new File(serverdir_files.getPath() + File.separator + "server.zip");
				Util.copy(f_template, dest);
				if(!Util.unzip(dest, serverdir_files))
					return -6;
				else {
					File lin = new File(serverdir_files.getPath() + File.separator + "cmdline-temp.txt");
					if(lin.exists() && lin.isFile() && !lin.isDirectory()) {
						Ini iniichan = new Ini(new File(serverdir.getPath() + File.separator + "server.ini"));
					    Scanner sc = new Scanner(lin);
					    if(sc.hasNextLine()) {
					    	iniichan.put("Server", "Cmdline", sc.nextLine());
							iniichan.store();
					    }
					    sc.close();
					    lin.delete();
					}
				}
			} catch (Throwable e) {
				e.printStackTrace();
				return -5;
			}
		}else if(f_template.getName().endsWith(".jar") || f_template.getName().endsWith(".jar")) {
			try {
				Util.copy(f_template, new File(serverdir_files.getPath() + File.separator + "server.jar"));
			} catch (Throwable e) {
				e.printStackTrace();
				return -5;
			}
		}else if(f_template.isDirectory()){
			try {
				Util.copy(f_template, serverdir_files);
			} catch (Throwable e) {
				e.printStackTrace();
				return -5;
			}
		}
		
		System.out.println("[Server # "+ id +"] Created successfully.\r\n");
		Main.servers.add(new Server(id));
		return id;
	}

	/**
	 * Deletes a server from the deployment directory and deregisters it.
	 * @param id ID of the server to be deleted
	 * @return true if successful.
	 */
	public static boolean Delete(int id) {
		try {
			Server srvr = Server.getByID(id);
			if(srvr != null) {
				srvr.kill();
				Util.deleteFileOrFolder(new File(Main.deploymentDir.getPath() + File.separator + srvr.getID()).toPath());
				Main.servers.remove(srvr);
				System.out.println("[Server #"+id+"] Deleted.");
				return true;
			}
			else {
				System.err.println("404: Server #"+ id +" not found.");
				return false;
			}
		}catch(Throwable t) {
			System.err.println("Error deleting server #"+ id +"\r\n");
			t.printStackTrace();
			return false;
		}
		
	}

	/**
	 * Searches an MCServer by ID
	 * @param id Server ID of the searched server
	 * @return The server found. NULL if not found
	 */
	public static Server getByID(int id) {
		for(Server srv : Main.servers) {
			if(srv.id == id)
				return srv;
			else continue;
		}
		return null;
	}
	
	public static ArrayList<String> getTemplates() {
		ArrayList<String> s = new ArrayList<String>();
		File f = Main.templatesDir;
		if(!f.exists()) {
			f.mkdir();
		}else if(!f.isDirectory()) {
			System.err.println("\r\n[!] Error: Template directory cannot be created.");
			return s;
		}
		for(String l : f.list())
			s.add(l);
		return s;
	}

	public ArrayList<String> getBackups() {
		ArrayList<String> s = new ArrayList<String>();
		File f = new File(this.dir.getPath() + File.separator + "backups");
		if(!f.exists()) {
			f.mkdir();
		}else if(!f.isDirectory()) {
			System.err.println("[!] Error: Backup directory cannot be created.");
			return s;
		}
		for(String l : f.list())
			s.add(l);
		return s;
	}
	
	public boolean createBackup(String desc) {
		File zip = new File(this.dir.getPath() + File.separator + "backups" + File.separator + desc + ".zip");
		if(zip.exists()) {
			System.err.println("[!] [Server # "+ this.id + "] Failure to create the backup: A backup with the description " + desc + " already exists.");
			return false;
		}
		
		try {
			if(!zip.createNewFile()) {
				System.err.println("[!] [Server # "+ this.id +"] Error creating the backup " + desc + ". Zip (Cannot create file)");
				return false;
			}
			if(Util.mkzip(new File(this.dir.getPath() + File.separator + "files"), zip)) {
				System.out.println("[Server # "+ this.id +"] backup created.");
				return true;
			}else {
				return false;
			}
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (SecurityException e) {
			System.err.println("[!] [Server # "+ this.id +"] Error creating the backup " + desc + ". Zip (Access denied)\r\n");
			return false;
		}
	}
	
	public boolean deleteBackup(String desc) {
		File zip = new File(this.dir.getPath() + File.separator + "backups" + File.separator + desc + ".zip");
		if(zip.exists()) {
			if(zip.delete()) {
				System.out.println("[Server # "+ this.id +"] Backup " + desc + " deleted.");
				
				return true;
			}else {
				System.err.println("[!] [Server # "+ this.id +"] An error occurred while deleting the backup " + desc + ".\r\n");
				return false;
			}
		}else {
			System.err.println("[!] Error: The backup to be deleted does not exist.");
			return false;
		}
	}

	public boolean applyBackup(String desc) {
		if(this.getBackups().contains(desc+ ".zip")) {
			System.out.println("[Server # "+ this.id +"] Backup " + desc + " is being uploaded. . .");
			
			File backup = new File(this.dir.getPath() + File.separator + "backups" + File.separator + desc + ".zip");
			if(!backup.exists() || backup.isDirectory()) {
				System.err.println("[!] [Server # "+ this.id +"] An error occurred while installing the backup " + desc + ". (0)");
				return false;
			}
			try {
				Util.deleteFileOrFolder(new File(this.dir.getPath() + File.separator + "files").toPath());
			} catch (IOException e) {
				System.err.println("[!] [Server # "+ this.id +"] An error occurred while installing the backup " + desc + ". (1)\r\n");
				e.printStackTrace();
				return false;
			}
			File files_dir = new File(this.dir.getPath() + File.separator + "files");
			if(!files_dir.exists() && !files_dir.mkdir()) {
				System.err.println("[!] [Server # "+ this.id +"] An error occurred while installing the backup " + desc + ". (2)");
				return false;
			}
			if(!Util.unzip(backup, files_dir)) {
				System.err.println("[!] [Server # "+ this.id +"] An error occurred while installing the backup " + desc + ". (3)\r\n");
				return false;
			}
			System.out.println("[Server # "+ this.id +"] Backup loaded successfully.\r\n");
			return true;
		}else {
			System.err.println("[!] Error: Couldn't upload unknown backup " + desc + ".\r\n");
			return false;
		}
		
		
		
	}

	
	
	
}
