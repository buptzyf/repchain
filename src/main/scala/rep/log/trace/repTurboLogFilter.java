package rep.log.trace;

import org.slf4j.Marker;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import rep.log.trace.LogTraceOption;


public class repTurboLogFilter extends TurboFilter {
	private LogTraceOption logOption = LogTraceOption.getLogTraceOption();
	 
	  @Override
	  public FilterReply decide(Marker marker, Logger logger, Level level,
	      String format, Object[] params, Throwable t) {
		  if (!isStarted()) {
		      return FilterReply.NEUTRAL;
		  }
		  
		  if(params != null && params.length > 0) {
			  Object sysname = params[0];
			  if(sysname != null) {
				  String nameobj = null;
				  if(sysname instanceof String[]) {
					  nameobj = ((String[])sysname)[0];
				  }
				  if(logOption.isOpenOutputLog(nameobj,logger.getName())) {
					  return FilterReply.ACCEPT;
				  }else {
					  return FilterReply.DENY;
				  }
			  }else {
				  if(logOption.isOpenOutputLog(null,logger.getName())) {
					  return FilterReply.ACCEPT;
				  }else {
					  return FilterReply.DENY;
				  }
			  }
		  }else {
			  if(logOption.isOpenOutputLog(null,logger.getName())) {
				  return FilterReply.ACCEPT;
			  }else {
				  return FilterReply.DENY;
			  }
		  }
	  }
	  
	  @Override
	  public void start() {
	      super.start(); 
	  }
}
