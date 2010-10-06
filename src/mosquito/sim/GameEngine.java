/* 
 * 	$Id: GameEngine.java,v 1.6 2007/11/28 16:30:47 johnc Exp $
 * 
 * 	Programming and Problem Solving
 *  Copyright (c) 2007 The Trustees of Columbia University
 */
package mosquito.sim;


import java.awt.geom.Line2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import mosquito.sim.GameListener.GameUpdateType;
import mosquito.sim.ui.GUI;
import mosquito.sim.ui.Text;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
 
 public final class GameEngine 
 {	
    static {
		PropertyConfigurator.configure("logger.properties");
    }

	private GameConfig config;
	private Board board;
	// private PlayerWrapper player;
	private int round;
	private ArrayList<GameListener> gameListeners;
	private Logger log;
	
	public GameEngine(String configFile)
	{
		config = new GameConfig(configFile);
		gameListeners = new ArrayList<GameListener>();
		board = new Board(10, 10);
		board.engine=this;
		if(config.getSelectedBoard() != null)
			try {
				board.load(config.getSelectedBoard());
			} catch (IOException e) {
				e.printStackTrace();
			}
	    log = Logger.getLogger(GameController.class);
	}

	public void addGameListener(GameListener l)
	{
		gameListeners.add(l);
	}

	public int getCurrentRound()
	{
		return round;
	}

	public GameConfig getConfig()
	{
		return config;
	}
	private Player curPlayer;
	public Board getBoard()
	{
		return board;
	}

	public boolean step()
	{
		if(board.mosquitosCaught >= config.getNumMosquitos()/2 || (config.getMaxRounds() > 0 && getCurrentRound() >= config.getMaxRounds()))
		{
			//GAME OVER!
			notifyListeners(GameUpdateType.GAMEOVER);
			return false;
		}
		try
		{
			for(Mosquito m : board.getMosquitos())
			{
				if(!m.caught)
				{
					int d = board.getDirectionOfLight(m.location);
					m.moveInDirection(d,board.getWalls());
					if(board.getCollector().contains(m))
					{
						m.caught = true;
						board.mosquitosCaught++;
					}
				}
			}
			
		}
		catch(ConcurrentModificationException e)
		{
			
		}
		notifyListeners(GameUpdateType.MOVEPROCESSED);
		round++;
		return true;
	}

	

	private final static void printUsage()
	{
		System.err.println("Usage: GameEngine <config file> gui");
		System.err.println("Usage: GameEngine <config file> text <board> <playerclass> <num mosquitos> <num lights> <long|short> {max rounds}");
	}

	public void removeGameListener(GameListener l)
	{
		gameListeners.remove(l);
	}
	private void notifyListeners(GameUpdateType type)
	{
		Iterator<GameListener> it = gameListeners.iterator();
		while (it.hasNext())
		{
			it.next().gameUpdated(type);
		}
	}

	public static final void main(String[] args)
	{

		if (args.length < 2 || args.length>8)
		{
			printUsage();
			System.exit(-1);
		}
		GameEngine engine = new GameEngine(args[0]);
		if (args[1].equalsIgnoreCase("text"))
		{
			// TextInterface ti = new TextInterface();
			// ti.register(engine);
			// ti.playGame(); 
			if(args.length < 7)
			{
				printUsage();
				System.exit(-1);
			}
			Text t = new Text(engine);
			engine.getConfig().setSelectedBoard(new File(args[2]));
			try {
				engine.getConfig().setPlayerClass((Class<Player>) Class.forName(args[3]));
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			engine.getConfig().setNumMosquitos(Integer.valueOf(args[4]));
			engine.getConfig().setNumLights(Integer.valueOf(args[5]));
			if(args[6].equals("long"))
				t.setLongMode(true);
			if(args.length == 8)
				engine.getConfig().setMaxRounds(Integer.valueOf(args[7]));
			t.play();
//			throw new RuntimeException("Text interface not implemented. Sorry.");
		} else if (args[1].equalsIgnoreCase("gui"))
		{
			
			new GUI(engine);
		}
		else if (args[1].equalsIgnoreCase("tournament"))
		{
//			runTournament(args, engine);
		}
		else
		{
			printUsage();
			System.exit(-1);
		}
	}

	public boolean setUpGame()
	{
		try
		{
			round = 0;
			board.load(config.getSelectedBoard());
			board.mosquitosCaught = 0;
			board.setLights(new HashSet<Light>());
			curPlayer = config.getPlayerClass().newInstance();
			curPlayer.Register();
			curPlayer.startNewGame(board.getWalls(), config.getNumLights());
			board.createMosquitos(0);
			if(config.getPlayerClass().getName().equals("mosquito.g0.InteractivePlayer"))
			{
				System.out.println("Entering interactive mode");
				board.setInteractive(true);
			}
			else
			{
				board.setInteractive(false);
				Set<Light> lights= curPlayer.getLights();
				if(lights == null)
				{
					System.err.println("Error: Player returned null for lights");
					return false;
				}
				if(lights.size() != config.getNumLights())
				{
					System.err.println("Error: You needed to give "  +config.getNumLights() +", but you gave " + lights.size() + " lights instead!");
					return false;
				}
				board.setLights(lights);
				Collector col = curPlayer.getCollector();
				if(col == null)
				{
					System.err.println("Error: Collector is null");
					return false;
				}
				for(Line2D w : board.getWalls())
				{
					if(col.intersects(w))
					{
						System.err.println("Error: Collector intersects walls");
						return false;
					}
				}
				for(Light l : lights)
				{
					if(col.contains(l.getLocation()))
					{
						System.err.println("Error: Collector intersects light");
						return false;
					}
				}
				board.setCollector(col);
				board.createMosquitos(config.getNumMosquitos());
			}
		} catch (IOException e)
		{
			log.error("Exception: " + e);
			return false;
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} 
		round = 0;
		notifyListeners(GameUpdateType.STARTING);
		return true;
	}

	public void mouseChanged() {
		notifyListeners(GameUpdateType.MOUSEMOVED);
	}
}
