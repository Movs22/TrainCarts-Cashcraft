package com.bergerkiller.bukkit.tc.utils;

public class StationParser {
	public static String parseStation(String string) {
			if(string.equalsIgnoreCase("") || string ==  null) return null;
			string = string.replaceAll("\\$CAI", "CC Airport Inter.");
			string = string.replaceAll("\\$A", "Ailsbury").replaceAll("\\$C", "Cashvillage").replaceAll("\\$F", "Fernhill")
					.replaceAll("\\$H", "Hemstead").replaceAll("\\$N", "New Arbridge");
			string = string.replaceAll("\\$Rd", "Road").replaceAll("\\$P", "Park");
			string = string.replaceAll("\\$Q", "Quarter").replaceAll("\\$S", "Shopping Centre").replaceAll("\\$R",
					"Racecourse");
			return string;
		}
	
	public static String[] listColor() {
		String[] colors = {"CHR","GAR","HS1","HS2","SVR","Purple","Blue","Green","Orange","Pink","Blue","Green","Orange","Red","Yellow","NAT","FTrams","Beige"};
		return colors;
	}
	
	public static String convertColor(String name) {
		name = name.split("/")[0];
		if (name.contains("$CHR"))
			return "#FF0000";
		if (name.contains("$GAR"))
			return "#1BF400";
		if (name.contains("$HS1"))
			return "#00946F";
		if (name.contains("$HS2"))
			return "#00DFFF";
		if (name.contains("$SVR"))
			return "#B955FF";
		if(name.contains("$Purple"))
			return "#54009f";
		if (name.contains("$Blue"))
			return "#387eff";
		if (name.contains("$Green"))
			return "#22D74C";
		if (name.contains("$Orange"))
			return "#FF7F27";
		if (name.contains("$Pink"))
			return "#FFAEC9";
		if (name.contains("$Red"))
			return "#ED1C24";
		if (name.contains("$Yellow"))
			return "#FCE600";
		if (name.contains("$NAT"))
			return "#0075B4";
		if (name.contains("$FTrams"))
			return "#BFAF81";
		if(name.contains("$Cyan"))
			return "#86c4bf";
		if(name.contains("$Beige"))
			return "#fce6a7";
		if(name.startsWith("$")) 
			return name.substring(1);
		return name;
	}
	
	public static String parseMetro(String string, String color) {
		String result = "[{\"text\":\"Change here for the \",\"color\":\"" + convertColor(color) + "\"}";
		String a = ", ";
		int b = 0;
		if(string.split(">").length < 1) return null;
		string = string.split(">")[0];
		if(string.equalsIgnoreCase("-")) return null;
		if (string.equalsIgnoreCase("")) return null;
		if (string == "") return null;
		for (int i = 0; i < string.length(); i++) {
			if (string.charAt(i) == 'B') {
				result = result + (", {\"text\":\"Blue\",\"color\":\"" + convertColor("$Blue") + "\"}");
			} else if (string.charAt(i) == 'G') {
				result = result + (", {\"text\":\"Green\",\"color\":\"" + convertColor("$Green") + "\"}");
			} else if (string.charAt(i) == 'O') {
				result = result + (", {\"text\":\"Orange\",\"color\":\"" + convertColor("$Orange") + "\"}");
			} else if (string.charAt(i) == 'P') {
				result = result + (", {\"text\":\"Pink\",\"color\":\"" + convertColor("$Pink") + "\"}");
			} else if (string.charAt(i) == 'L') {
				result = result + (", {\"text\":\"Purple\",\"color\":\"" + convertColor("$Purple") + "\"}");
			} else if (string.charAt(i) == 'C') {
				result = result + (", {\"text\":\"Cyan\",\"color\":\"" + convertColor("$Cyan") + "\"}");
			} else if (string.charAt(i) == 'R') {
				result = result + (", {\"text\":\"Red\",\"color\":\"" + convertColor("$Red") + "\"}");
			} else if (string.charAt(i) == 'Y') {
				result = result + (", {\"text\":\"Yellow\",\"color\":\"" + convertColor("$Yellow") + "\"}");
			} else {
				if (b == string.length() - 1) {
					if (b == string.length() - 1)
						a = " Line" + (string.length() > 1 ? "s" : "") + ".";
					result = result + (", {\"text\":\"" + a + "\",\"color\":\"" + convertColor(color) + "\"}");
					result = result + "]";
				}
				b += 1;
				continue;
			}
			if (b == string.length() - 1)
				a = " Line" + (string.length() > 1 ? "s" : "") + ".";
			if (b == string.length() - 2)
				a = " and ";
			if (b < string.length() - 2)
				a = ", ";
			result = result + (", {\"text\":\"" + a + "\",\"color\":\"" + convertColor(color) + "\"}");
			if (b == string.length() - 1) {
				result = result + "]";
			}
			b += 1;
		}
		return result;
	}

	public static String parseRail(String string, String color) {
		String result = "[{\"text\":\"Also change here for \",\"color\":\"" + convertColor(color) + "\"}";
		String a = ", ";
		int b = 0;
		if(string.split(">").length < 2) return null;
		string = string.split(">")[1];
		if(string.equalsIgnoreCase("-")) return null;
		if (string.equalsIgnoreCase("")) return null;
		for (int i = 0; i < string.length(); i++) {
			if (string.charAt(i) == '1') {
				result = result + ", {\"text\":\"High Speed 1\",\"color\":\"" + convertColor("$HS1") + "\"}";
			} else if (string.charAt(i) == '2') {
				result = result + (", {\"text\":\"High Speed 2\",\"color\":\"" + convertColor("$HS2") + "\"}");
			} else if (string.charAt(i) == 'S') {
				result = result + (", {\"text\":\"South Valley Railway\",\"color\":\"" + convertColor("$SVR") + "\"}");
			} else if (string.charAt(i) == 'A') {
				result = result
						+ (", {\"text\":\"Greater Arbridge Railway\",\"color\":\"" + convertColor("$GAR") + "\"}");
			} else if (string.charAt(i) == 'N') {
				result = result + (", {\"text\":\"New Arbridge Trams\",\"color\":\"" + convertColor("$NAT") + "\"}");
			} else if (string.charAt(i) == 'F') {
				result = result + (", {\"text\":\"Fernhill Trams\",\"color\":\"" + convertColor("$FTrams") + "\"}");
			} else if (string.charAt(i) == 'C') {
				result = result
						+ (", {\"text\":\"Cashvillage Heritage Rail\",\"color\":\"" + convertColor("$CHR") + "\"}");
			} else {
				if (b == string.length() - 1) {
					if (b == string.length() - 1)
						a = " services.";
					result = result + (", {\"text\":\"" + a + "\",\"color\":\"" + convertColor(color) + "\"}");
					result = result + "]";
				}
				b += 1;
				continue;
			}
			if (b == string.length() - 1)
				a = " services.";
			if (b == string.length() - 2)
				a = " and ";
			if (b < string.length() - 2)
				a = ", ";
			result = result + (", {\"text\":\"" + a + "\",\"color\":\"" + convertColor(color) + "\"}");
			if (b == string.length() - 1) {
				result = result + "]";
			}
			b += 1;
		}
		return result;
	}
}

