package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.StationParser;
import com.bergerkiller.bukkit.tc.utils.TimeDurationFormat;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

public class ArrivalSigns {
    private static HashMap<String, TimeSign> timerSigns = new HashMap<>();
    private static BlockMap<TimeCalculation> timeCalculations = new BlockMap<>();
    private static TimeDurationFormat timeFormat = new TimeDurationFormat("mm:ss");
    private static Task updateTask;

    public static TimeSign getTimer(String name) {
        return timerSigns.computeIfAbsent(name, new_timesign_name -> new TimeSign(new_timesign_name));
    }

    public static boolean isTrigger(Sign sign) {
        SignActionHeader header = SignActionHeader.parseFromSign(sign);
        return header.isValid() && Util.getCleanLine(sign, 1).equalsIgnoreCase("trigger");
    }

    public static void trigger(Sign sign, MinecartMember<?> mm) {
    	trigger(sign, mm, null, null, false, null, false);
    }
    
    public static void trigger(Sign sign, MinecartMember<?> mm, String name, String duration, boolean raw) {
    	trigger(sign, mm, name, duration, raw, null, false);
    }
    
    public static void trigger(Sign sign, MinecartMember<?> mm, String name, String duration, boolean raw, String destination) {
    	trigger(sign, mm, name, duration, raw, destination, false);
    }
    
    public static void trigger(Sign sign, MinecartMember<?> mm, String name, String duration, boolean raw, String destination, boolean override) {
        if (!TCConfig.SignLinkEnabled) return;
        if(!raw || name == null) {
        	return;
        }
        if(duration.startsWith("-")) {
        	TimeSign t = getTimer(name);
        	t.timeout = ParseUtil.parseTime(duration);
        	if(mm == null || mm.isUnloaded() || mm == null || mm.getEntity().isDestroyed()) return;
            //t.duration = ParseUtil.parseTime(duration);
            t.trigger(mm.getGroup(), ParseUtil.parseTime(duration));
            t.update();
    		return;
    	}
        if (name.isEmpty()) return;
        TimeSign t = getTimer(name);
        if ( mm != null) {
        	if(mm == null || mm.isUnloaded() || mm == null || mm.getEntity().isDestroyed()) return;
            if (ParseUtil.parseTime(duration) == 0) {
                timeCalcStart(sign.getBlock(), mm);
            } else {
                t.trigger(mm.getGroup(), ParseUtil.parseTime(duration));
                t.update();
            }
        }
    }

    
    

    public static void setTimeDurationFormat(String format) {
        try {
            timeFormat = new TimeDurationFormat(format);
        } catch (IllegalArgumentException ex) {
            TrainCarts.plugin.log(Level.WARNING, "Time duration format is invalid: " + format);
        }
    }

    public static void updateAll() {
        for (TimeSign t : timerSigns.values()) {
            if (!t.update()) {
                return;
            }
        }
    }

    public static void init(String filename) {
        FileConfiguration config = new FileConfiguration(filename);
        config.load();
        for (String key : config.getKeys()) {
            String dur = config.get(key, String.class, null);
            if (dur != null) {
                TimeSign t = getTimer(key);
                //t.startTime = System.currentTimeMillis();
                //t.duration = ParseUtil.parseTime(dur);
            }
        }
    }
    public static void save(String filename) {
        FileConfiguration config = new FileConfiguration(filename);
        for (TimeSign sign : timerSigns.values()) {
            config.set(sign.name, sign.getDuration());
        }
        config.save();
    }

    public static void deinit() {
        timerSigns.clear();
        timerSigns = null;
        timeCalculations.clear();
        timeCalculations = null;
        if (updateTask != null && updateTask.isRunning()) {
            updateTask.stop();
        }
        updateTask = null;
    }

    
    public static void timeCalcStart(Block signblock, MinecartMember<?> member) {
    	timeCalcStart(signblock, member, false, 3, false);
    }
    public static void timeCalcStart(Block signblock, MinecartMember<?> member, Boolean skip, int line) {
    	timeCalcStart(signblock, member, skip, line, false);
    }
    public static void timeCalcStart(Block signblock, MinecartMember<?> member, Boolean skip, int line, Boolean a) {
    	if(skip == false) {
    		Sign sign = (Sign) signblock.getState(); //BlockUtil.getSign(this.signblock);
    		if(line < 4 && !sign.getLine(line).equalsIgnoreCase("")) return;
    		TimeCalculation calc = new TimeCalculation();
    		calc.startTime = System.currentTimeMillis();
    		calc.signblock = signblock;
    		calc.member = member;
    		if(a == false) {
    			calc.hasMoved = true;
    		} else {
    			calc.hasMoved = false;
    		}
    		for (Player player : calc.signblock.getWorld().getPlayers()) {
    			if (player.hasPermission("train.build.trigger")) {
    				if (member == null) {
    					player.sendMessage(ChatColor.YELLOW + "[Train Carts] Remove the power source to stop recording");
    				} else {
    					player.sendMessage(ChatColor.YELLOW + "[Train Carts] Stop or destroy the minecart to stop recording");
    				}
    			}
    		}

    		calc.setLine(line);
    		timeCalculations.put(calc.signblock, calc);
    	}
        if (updateTask == null) {
            updateTask = new Task(TrainCarts.plugin) {
                public void run() {
                    if (timeCalculations.isEmpty()) {
                        this.stop();
                        updateTask = null;
                    }
                    for (TimeCalculation calc : timeCalculations.values()) {
                        if (calc.member != null) {
                            if (calc.member.isUnloaded() || calc.member.getEntity().getEntity().isDead() || (!calc.member.getEntity().isMoving() && calc.hasMoved == true)) {
                                calc.setTime(calc.Line);
                                timeCalculations.remove(calc.signblock);
                                return;
                            } else if(calc.member.getEntity().isMoving()) {
                            	calc.setMoved(true);
                            }
                        }
                    }
                }
            }.start(0, 1);
        }
    }
    
    public static void timeCalcStartMDest(Block signblock, MinecartMember<?> member, int RouteIndex, int A, int B, Boolean a) {
        TimeCalculation calc = new TimeCalculation();
        calc.startTime = System.currentTimeMillis();
        calc.signblock = signblock;
        calc.member = member;
        for (Player player : calc.signblock.getWorld().getPlayers()) {
            if (player.hasPermission("train.build.trigger")) {
                if (member == null) {
                    player.sendMessage(ChatColor.YELLOW + "[Train Carts] Remove the power source to stop recording");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "[Train Carts] Stop or destroy the minecart to stop recording");
                }
            }
        }
        calc.setLine((RouteIndex == 0 ? A : B));
        timeCalculations.put(calc.signblock, calc);
        timeCalcStart(signblock, member, true, (RouteIndex == 1 ? A : B), a);
        
    }

    public static void timeCalcStop(Block signblock) {
        TimeCalculation calc = timeCalculations.get(signblock);
        if (calc != null && calc.member == null) {
            calc.setTime(calc.Line);
            timeCalculations.remove(signblock);
        }
    }

    private static class TimeCalculation {
        public long startTime;
        public Block signblock;
        public int Line;
        public Boolean hasMoved = false;
        public void setLine(int line) {
        	this.Line = line;
        }
        public void setMoved(Boolean b) {
        	hasMoved = b;
        }
        public MinecartMember<?> member = null;
        public void setTime(int line) {
            long duration = (long) Math.ceil(System.currentTimeMillis() - startTime);
            if (MaterialUtil.ISSIGN.get(this.signblock)) {
                Sign sign = (Sign) this.signblock.getState(); //BlockUtil.getSign(this.signblock);
                if(line < 4 && !sign.getLine(line).equalsIgnoreCase("")) return;
                String dur = timeFormat.format(duration);
                if(line > 3) {
                	Sign sign2 = (Sign) this.signblock.getWorld().getBlockAt(sign.getX(), sign.getY() - 1,sign.getZ()).getState();
                	if(sign2 == null) {
                		for (Player player : sign.getWorld().getPlayers()) {
                            if (player.hasPermission("train.build.trigger")) {
                                player.sendMessage(ChatColor.RED + "[Train Carts] Failed to set the time of '" + sign.getLine(2) + "'. Line " + line + " is higher then the maximum amount (4).");
                            }
                        }
                	}
                	sign2.setLine(line - 4, dur);
                	sign2.update(true);
                } else {
                	sign.setLine(line, dur);
                	sign.update(true);
                }
                //Message
                for (Player player : sign.getWorld().getPlayers()) {
                    if (player.hasPermission("train.build.trigger")) {
                        player.sendMessage(ChatColor.YELLOW + "[Train Carts] Trigger time of '" + StationParser.parseStation(sign.getLine(3)) + "' set to " + dur);
                    }
                }
            }
        }
    }
   
    
    public static class TimePrediction {
    	public long startTime = -1;
    	public long duration = -1;
    	public MinecartGroup group;
    	public Boolean groupOverride = false;
    	public long timeout = -30000L;
    	public Boolean inverted = false;
    	public TimePrediction(long duration, long timeout, MinecartGroup group, Boolean inverted) {
    		this.duration = duration;
    		this.startTime = System.currentTimeMillis();
    		this.timeout = timeout;
    		if(group != null) {
    			this.group = group;
    		} else {
    			this.group = null;
    			this.groupOverride = true;
    		}
    		this.inverted = inverted;
    		if(duration < 0L) {
    			this.inverted = true;
    		}
    	}
    	
    	public long Remaining() {
    		return duration - System.currentTimeMillis() + startTime;
    	}
    	
    	public static void Log(TimePrediction t) {
    		if(t.Remaining() < 0) System.out.println(t + " - " + t.duration + " - " + t.startTime + " - " + t.Remaining());
    	}
    	
    	public static String Debug(TimePrediction t) {
    		return (t + " - " + t.duration + " - " + t.startTime + " - " + t.Remaining());
    	}
    	
    }
    public static class TimeSign {
    	public List<TimePrediction> predictions = new ArrayList<TimePrediction>();
        /*public long startTime = -1;
        public long FstartTime = -1;
        public long duration;
        public long Fduration;
        public MinecartGroup group;
        public MinecartGroup Fgroup;*/
        private String name;
        public long timeout = -1000L;
        //public Boolean FgroupOverride = false;
        //public Boolean inverted = false;
        public TimeSign(String name) {
            this.name = name;
        }

        public void trigger(MinecartGroup mm, Long d) {
        	TimePrediction p = new TimePrediction(d, timeout, mm, false);
        	if(!predictions.isEmpty()) {
        		for(Iterator<TimePrediction> t = predictions.iterator(); t.hasNext(); ) {
        			TimePrediction t2 = t.next();
        				if(t2.Remaining() < timeout && t2.duration > 0) {
        					t.remove();
        				}
        				if(Math.round(t2.Remaining()/5000) == Math.round(p.Remaining()/5000)) {
        					return;
        				};
        		};
        	}
        	predictions.add(p);
        	Collections.sort(predictions, Comparator.comparing(TimePrediction::Remaining));
        	timerSigns.putIfAbsent(name, this);
        }

        public String getName() {
            return this.name;
        }

        public String getDuration() {
        	if(predictions.size() < 1) return "No services";
        	TimePrediction p = predictions.get(0);
            long remaining = p.Remaining();
            for(Iterator<TimePrediction> t = predictions.iterator(); t.hasNext(); ) {
    			TimePrediction t2 = t.next();
    				if(t2.Remaining() < -10000L && t2.duration > 0) {
    					t.remove();
    				}
    		};
            if(remaining < 0L && p.duration < 0L) {
            	if(p.inverted) {
            		if(remaining < (p.duration*2) ) {
            			predictions.remove(0);
            			if(!predictions.isEmpty()) {
                    		for(Iterator<TimePrediction> t = predictions.iterator(); t.hasNext(); ) {
                    			TimePrediction t2 = t.next();
                    				if(t2.Remaining() < -1000L) {
                    					t.remove();
                    				}
                    		};
                    	}
            			if(predictions.size() < 1) return "No services";
            			Collections.sort(predictions, Comparator.comparing(TimePrediction::Remaining));
            			predictions.forEach(t -> TimePrediction.Log(t));
            			remaining = predictions.get(0).Remaining();
            			int time = (int) Math.ceil(0.001 * remaining); // msec -> sec
                    	int seconds = (int) (time % 60);
                    	int minutes = (int) (time % 3600) / 60;
                    	StringBuilder rval = new StringBuilder(6);
                    // 	Minutes
                    	rval.append(minutes).append(':');
                    // 	Seconds
                    	if (seconds < 10) {
                    		rval.append('0');
                    	}
                    	rval.append(seconds);
                    	return rval.toString();
            		}
            		if(remaining < (p.duration*2+2500) ) {
            			return "Departing";
            		}
            		return "Arrived";
            	}
            }
            if(remaining < 0) remaining = 0;
            	int time = (int) Math.ceil(0.001 * remaining); // msec -> sec
            	int seconds = (int) (time % 60);
            	int minutes = (int) (time % 3600) / 60;
            	StringBuilder rval = new StringBuilder(6);
            // 	Minutes
            	rval.append(minutes).append(':');
            // 	Seconds
            	if (seconds < 10) {
            		rval.append('0');
            	}
            	rval.append(seconds);
            	return rval.toString();
        }

        @SuppressWarnings("deprecation")
		public boolean update() {
            if (!TCConfig.SignLinkEnabled) return false;
            String dur = getDuration();
            if(dur.equals(Variables.get(this.name + 'S').get())) return true;
            Variables.get(this.name + 'S').set(dur);
            if (dur.equals("No services")) {
            	timerSigns.remove(this.name);
                return false;
            }
            if (dur.equals("0:00")) {
            	Variables.get(this.name + 'S').set("0:01");
                return true;
            } else {
                return true;
            }
        }
    }
			
}
