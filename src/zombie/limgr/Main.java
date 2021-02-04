package zombie.limgr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import org.ini4j.Ini;

import zombie.limgr.interfaces.CLI;
import zombie.limgr.interfaces.HTTPD;
import zombie.limgr.interfaces.SQ;

public class Main {

	public static String version = "v1.6pb";
	public static String appname = "LiMGR";
	public static Ini config;
	public static Lang lang;
	
	public static File deploymentDir;
	public static File templatesDir;
	public static ArrayList<Server> servers;
	
	/* Interfaces ^_^ */
	private static CLI cli; //Command line
	public static HTTPD httpd; //Web interface
	public static SQ sq; //ServerSocket
	
	public static String textheader = "L i  M G R\n(c) Zombie 2021\n";
	
	public static void main(String[] args) {
		System.out.println(textheader);
		System.out.println("Version: " + version);
		
		//Environment Check
		try {
			System.out.println("Deployment-Path: " + Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
		} catch (Exception e1) {
			System.out.println("Deployment-Path: " + "ERROR");
			return;
		}
		
		//Load Config
		if(!loadConfig())return;
		
		//Load Human Language
		lang = new Lang(config.get("Language", "language"));
		try {
			lang.load();
		} catch (Exception e2) {
			System.err.println("[!] Error: Couldn't load human language file.");
			e2.printStackTrace();
			return;
		}
		
		File templates = new File("templates");
		if(!templates.exists()) if(!templates.mkdir()) {
				System.err.println("[!] Error: Couldn't create templates directory.");
				return;
		}
		
		
		
		//Init Dirs
		if(!loadDirs())return;
		
		//load MCServer
		loadServers();
		
		//Try to start web interface until its running, if enabled.
		int trys = 3;
		while (trys>0) {
			if(!(config.get("HTTPD").get("enabled").equalsIgnoreCase("true"))) {
				System.out.println("Web server port: <DISABLED>");
				break;
			}
			try {
				httpd = new HTTPD(config);
				httpd.Start();
			} catch (Throwable e) {
				System.err.println("[!] Error starting the web interface. (Still trying"+trys+" times)");
				e.printStackTrace();
				try {
					Thread.sleep(1200L);
				} catch (InterruptedException e1) {
					System.out.println("Interrupted.");
					break;
				}
				trys--;
				continue;
			}
			break;
		}
		
		trys = 3;
		while (trys>0) {
			if(!(config.get("ServerQuery").get("enabled").equalsIgnoreCase("true"))) {
				System.out.println("Web server port: <DISABLED>");
				break;
			}
			try {
				sq = new SQ(config);
				sq.Start();
			} catch (Throwable e) {
				System.err.println("/!\\ Error starting the ControlSocket Interface. (Still trying"+trys+" times)");
				e.printStackTrace();
				try {
					Thread.sleep(1200L);
				} catch (InterruptedException e1) {
					System.out.println("Canceled.");
					break;
				}
				trys--;
				continue;
			}
			break;
		}
		
		//Shutdown Hook
		Runtime.getRuntime().addShutdownHook(new Thread()
		{
		    @Override
		    public void run()
		    {
		    	if(httpd != null)
		    		httpd.Stop();
		    	for(Server srv : servers)
					srv.kill();
		    	System.out.println("\n\nBye, Hacker-san :3");
		    }
		});
		
		System.out.println("\n Done! Enter \\ \"help \\\" to list commands");
		
		//Finally, start the [C]ommand [L]ine [I]nterface
		cli = new CLI();
		Scanner scan = new Scanner(System.in);
		while (scan.hasNext()){
			String s = scan.nextLine();
			String[] cmd = s.trim().split("\\s+");
			
			if(cmd[0].equalsIgnoreCase("quit")) {
				break;
			}else {
				cli.command(cmd);
			}
			System.out.println("");
			continue;
		}
		scan.close();
	}
	
	/**
	 * Initializes settings file
	 * @return
	 */
	private static boolean loadConfig() {
		File inifile = new File("config.ini");
		if(!inifile.exists()) { //Create default if not found.
			System.out.println("Settings file not found, create default.");
			try { 
				FileOutputStream f = new FileOutputStream(inifile); 
				String defaultConfig = 
						"[Language]\n"+
						"language=en_GB\n"+
						"[HTTPD]\n" + 
						"enabled=true\n" +
						"port=9000\n" +
						"passwd=$2a$10$GKrVPAsVEFYYUS1di0iej.A8f2oimGTnoAo0xPBDX/TAugr9Rf5Na\n" + //Default: imnotgerman
						"\n" +
						"[ServerQuery]\n" +
						"enabled=false\n" +
						"port=9002\n" +
						"authkey=h1zZdasIsjfelAdfo93Ashj31erHeilSatan666asadsdfLolicon5\n" +
						"\n" +
						"[Dirs]\n" +
						"templates-dir=templates\n" +
						"deploymnt-dir=servers\n" +
						"\n" +
						"[Limits]\n" +
						"port-range=25000-25999";
				f.write(defaultConfig.getBytes("UTF-8"));
				f.flush();
				f.close();
			} catch (IOException e) {
				System.err.println("/!\\_ Error creating the default settings file.");
				e.printStackTrace();
			}
		}
		try {
			config = new Ini(inifile);
		} catch (IOException e) {
			System.err.println("/!\\_ Error loading settings file.");
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	/**
	 * Initializes all directories according to the settings file.
	 * Obviously: The settings file must have been loaded beforehand.
	 * @return false on error
	 */
	private static boolean loadDirs() {
		//Server deployment directory
		if(config.get("Dirs", "deploymnt-dir") == null) {
			System.err.println("[!] Error: Settings file invalid. (deploymnt-dir = zero !!!)");
			return false;
		}
		deploymentDir = new File(config.get("Dirs", "deploymnt-dir"));
		if(!deploymentDir.exists()) {
			System.out.println("\r\nDeployment directory not found, create default.");
			deploymentDir.mkdir();
		}
		else if(deploymentDir.isFile()) {
			System.err.println("\r\n[!] Error creating the deployment directory.");
			return false;
		}
		
		//Server templates directory
		if(config.get("Dirs", "templates-dir") == null) {
			System.err.println("[!] Error: Settings file invalid. (templates-dir = null !!!)");
			return false;
		}
		templatesDir = new File(config.get("Dirs", "templates-dir"));
		if(!deploymentDir.exists()) {
			System.out.println("\r\nServer template directory not found, create default.");
			deploymentDir.mkdir();
		}
		else if(deploymentDir.isFile()) {
			System.err.println("[!] Error creating the server template directory.");
			return false;
		}
		
		return true;
	}
	
	/**
	 * Initializes servers in the deployment directory.
	 */
	private static void loadServers() {
		servers = new ArrayList<Server>();
		for(File f : deploymentDir.listFiles()) {
			if(!f.isDirectory())
				continue;
			if(!Arrays.asList(f.list()).contains("files"))
				continue;
			try {
				servers.add(new Server(Integer.parseInt(f.getName())));
			}catch (NumberFormatException ex) {
				continue;
			}
		}
	}
}
