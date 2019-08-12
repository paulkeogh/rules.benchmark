package com.anam.rules.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)					// Prints benchmark results using milliseconds as time unit
@Fork(value = 2, jvmArgs = {"-Xms1G", "-Xmx1G"})
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
public class RulesBenchmark {
    private static final String TARGET = "456789";
	private static final long patternCount = 50000;
	
    @State(Scope.Benchmark)
    public static class MyState {
    	private final List<Pattern> patterns = new ArrayList<>();
    	final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(16);
    	final CompletionService<Boolean> completionService = new ExecutorCompletionService<Boolean>(executor);;
    	
    	@TearDown(Level.Trial)
        public void tearDown() {
        	System.gc();
        }
        
        @Setup
        public void setUp() {
        	Pattern pattern = Pattern.compile("^123.*");
        	Pattern patternFinal = Pattern.compile("^456.*");
        	
        	patterns.clear();
        	
        	LongStream.range(0, patternCount)
        	  .forEach(index -> {
        		  patterns.add(pattern);
        	  }); 
        	
        	patterns.add(patternFinal);
        	
        }
    }
    
    @Benchmark
    public void patternMatchByExecutorFixedThreadPoolInvoke(MyState state) {
        final List<Future<Boolean>> futures = new ArrayList<>(state.patterns.size());
    	final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(8);
       
    	state.patterns.stream().parallel().forEach(pattern -> {
            Callable<Boolean> runnableTask = () -> {
               	Matcher matcher = pattern.matcher(TARGET);
               	return matcher.find();
            };
            
            // Submit task immediately
            futures.add(executor.submit(runnableTask));
    	});
    	
    	boolean isMatched = false;
    	
    	try {
	    	for (Future<Boolean> result : futures) {
	    		if (result.get() == true) {
	    			isMatched = true;
	    			break;
	    		}
	    	}
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
    	
    	assert isMatched == true;
    	executor.shutdownNow();
    }
    
    @Benchmark
    public void patternMatchByExecutorFixedThreadPoolInvokeAll(MyState state) {
        final List<Callable<Boolean>> futures = new ArrayList<>(state.patterns.size());
    	final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(8);
       
    	state.patterns.stream().parallel().forEach(pattern -> {
            Callable<Boolean> runnableTask = () -> {
               	Matcher matcher = pattern.matcher(TARGET);
               	return matcher.find();
            };
            
            futures.add(runnableTask);
    	});
    	
    	boolean isMatched = false;
    	
    	try {
			List<Future<Boolean>> results = executor.invokeAll(futures);
	    	
	    	for (Future<Boolean> result : results) {
	    		if (result.get() == true) {
	    			isMatched = true;
	    		  break;
	    		}	
	    	}
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
    	
    	assert isMatched == true;
    	executor.shutdownNow();
    }
    
    @Benchmark
    public void patternMatchByStream(MyState state) {
    	List<Pattern> results = state.patterns.stream().filter(pattern -> {
    		Matcher matcher = pattern.matcher(TARGET);
    		
    		if (matcher.find()) 
    			return true;
    		else 
    			return false;
    	}).collect(Collectors.toList());
    	
    	assert results.size() == 1;
   }
    
    @Benchmark
    public void patternMatchByParallelStream(MyState state) {
    	List<Pattern> results = state.patterns.stream()
    		.parallel()
    		.filter(pattern -> {
	    		Matcher matcher = pattern.matcher(TARGET);
	    		
	    		if (matcher.find()) 
	    			return true;
	    		else 
	    			return false;
    	}).collect(Collectors.toList());
    	
    	assert results.size() == 1;
    }

    @Benchmark
    public void patternMatchBySequence(MyState state) {
    	int count = 0;
    	
    	for (Pattern p : state.patterns) {
    		Matcher matcher = p.matcher(TARGET);
    		if (matcher.find() == true) {
    			count++;
    			break;
    		}
    	}
    	
    	assert count == 1;
    }
}
