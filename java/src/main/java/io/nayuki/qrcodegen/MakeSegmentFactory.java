public class MakeSegmentFactory {
	public static MakeSegment getMakeSegment(String text) {
		MakeSegment makeSegment = null;
		
		if (text.equals(""));  // Leave result empty
		else if (QrSegment.NUMERIC_REGEX.matcher(text).matches())
			makeSegment = new MakeNumericToSegment();
		else if (QrSegment.ALPHANUMERIC_REGEX.matcher(text).matches())
			makeSegment = new MakeAlphaNumericToSegment();
		else
			makeSegment = new MakeBytesToSegment();
		
		return makeSegment;
	}
}