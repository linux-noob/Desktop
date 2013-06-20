package org.freeplane.plugin.remote.server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.docear.messages.Messages.CloseAllOpenMapsRequest;
import org.docear.messages.Messages.CloseUnusedMaps;
import org.docear.messages.models.MapIdentifier;
import org.docear.messages.models.UserIdentifier;
import org.freeplane.features.mapio.MapIO;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.plugin.remote.server.InternalMessages.ReleaseTimedOutLocks;
import org.freeplane.plugin.remote.server.actors.MainActor;
import org.freeplane.plugin.remote.server.v10.Actions;
import org.jboss.netty.channel.ChannelException;
import org.slf4j.Logger;

import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;

import com.typesafe.config.ConfigFactory;

public class RemoteController {
	
	private final ActorSystem system;
	private final ActorRef mainActor;
	private final Cancellable closeUnusedMapsJob;
	private final Cancellable releaseExpiredLocksJob;
	private final Map<MapIdentifier, OpenMindmapInfo> mapIdentifierInfoMap = new HashMap<MapIdentifier, OpenMindmapInfo>();
	
	private static RemoteController instance;
	public static RemoteController getInstance() throws ChannelException{
		if(instance == null)
			instance = new RemoteController();
		return instance;
	}

	private RemoteController() {
		final Logger logger = getLogger();
		
		//change class loader
		final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(Activator.class.getClassLoader());
		
		logger.info("starting Remote Plugin...");
	
		system = ActorSystem.create("freeplaneRemote", ConfigFactory.load().getConfig("listener"));
		mainActor = system.actorOf(new Props(MainActor.class), "main");
		logger.info("Main Actor running at path='{}'", mainActor.path());

		closeUnusedMapsJob = 
				system.scheduler().schedule(
						Duration.Zero(), 
						Duration.apply(10, TimeUnit.MINUTES), 
						new Runnable() {
							@Override
							public void run() {
								logger.trace("Scheduling closing of unused maps.");
								mainActor.tell(new CloseUnusedMaps(new UserIdentifier("self", ""), 600000), null); // ten minutes
							}
						}, system.dispatcher());
		
		releaseExpiredLocksJob = 
				system.scheduler().schedule(
						Duration.Zero(), 
						Duration.apply(5, TimeUnit.SECONDS), 
						new Runnable() {
							@Override
							public void run() {
								logger.trace("Scheduling release of locks that timed out.");
								mainActor.tell(new ReleaseTimedOutLocks(15000L), null); // 15 seconds
							}
						}, system.dispatcher());
		
		//set back to original class loader
		Thread.currentThread().setContextClassLoader(contextClassLoader);
	}
	
	public static boolean isStarted(){
		return instance != null;
	}

	public static void stop() {
		getLogger().info("Shutting down remote plugin...");
		RemoteController controller = getInstance();
		controller.closeUnusedMapsJob.cancel();
		controller.releaseExpiredLocksJob.cancel();
		controller.saveAndCloseMaps();
		//polling to wait for all maps to be closed
		long pollingUntilHardShutdown = 60000; //1 min
		final long startPoll = System.currentTimeMillis();
		long currentPollingTime = 0;
		while(getMapIdentifierInfoMap().size() > 0 && currentPollingTime < pollingUntilHardShutdown) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			currentPollingTime = System.currentTimeMillis() - startPoll;
		}
		
		controller.mainActor.tell(PoisonPill.getInstance(), null);
		controller.system.shutdown();
		
		instance = null;
	}
	
	private void saveAndCloseMaps() {
		Actions.saveAndCloseAllOpenMaps(new CloseAllOpenMapsRequest(new UserIdentifier("self", "")));
	}

	public static ModeController getModeController() {
		return MModeController.getMModeController();
	}
	
	public static MapIO getMapIO() {
		return getModeController().getExtension(MapIO.class);
	}

	public static Map<MapIdentifier, OpenMindmapInfo> getMapIdentifierInfoMap() {
		return getInstance().mapIdentifierInfoMap;
	}
	
	public static Logger getLogger() {
		return org.freeplane.plugin.remote.server.Logger.getLogger();
	}
	
	public static ActorSystem getActorSystem() {
		return getInstance().system;
	}
}
