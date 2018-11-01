use super::{Version, QrSegment, QrSegmentMode, ALPHANUMERIC_CHARSET, QrCode, QrCodeEcc};
#[cfg(feature = "kanji")]
use super::BitBuffer;

/// Splits text into optimal segments and encodes kanji segments.
pub struct QrSegmentAdvanced {}

#[cfg(feature = "kanji")]
const MODE_TYPES: [QrSegmentMode; 4] = [QrSegmentMode::Byte, QrSegmentMode::Alphanumeric, QrSegmentMode::Numeric, QrSegmentMode::Kanji];
#[cfg(not(feature = "kanji"))]
const MODE_TYPES: [QrSegmentMode; 3] = [QrSegmentMode::Byte, QrSegmentMode::Alphanumeric, QrSegmentMode::Numeric];
#[cfg(feature = "kanji")]
const NUM_MODES: usize = 4;
#[cfg(not(feature = "kanji"))]
const NUM_MODES: usize = 3;

/// Returns a list of zero or more segments to represent the specified Unicode text string.
pub fn make_segments_optimally(code_points: &[char], ecc: QrCodeEcc, min_version: Version, max_version: Version) -> Option<Vec<QrSegment>> {
    let min_version = min_version.value();
    let max_version = max_version.value();

    // Check arguments
    if min_version > max_version {
        return None;
    }

    // Iterate through version numbers, and make tentative segments
    let mut segs = Vec::new();

    for version in min_version..=max_version {
        if version == min_version || version == 10 || version == 27 {
            segs = make_segments_optimally_at_version(&code_points, Version::new(version));
        }
        let version = Version::new(version);

        // Check if the segments fit
        let data_capacity_bits = QrCode::get_num_data_codewords(version, ecc) * 8;
        let data_used_bits = QrSegment::get_total_bits(&segs, version);

        if let Some(data_used_bits) = data_used_bits {
            if data_used_bits <= data_capacity_bits {
                return Some(segs); // This version number is found to be suitable
            }
        }
    }

    None
}

// Returns a new list of segments that is optimal for the given text at the given version number.
fn make_segments_optimally_at_version(code_points: &[char], version: Version) -> Vec<QrSegment> {
    let char_modes = compute_character_modes(code_points, version);
    split_into_segments(code_points, &char_modes)
}

// Returns a new array representing the optimal mode per code point based on the given text and version.
fn compute_character_modes(code_points: &[char], version: Version) -> Vec<QrSegmentMode> {
    // Segment header sizes, measured in 1/6 bits
    let mut head_costs = [0usize; NUM_MODES];

    for i in 0..NUM_MODES {
        head_costs[i] = (4 + MODE_TYPES[i].num_char_count_bits(version) as usize) * 6;
    }

    // charModes[i][j] represents the mode to encode the code point at index i
    // such that the final segment ends in modeTypes[j] and the total number of bits is minimized over all possible choices
    let mut char_modes = vec![[None::<QrSegmentMode>; NUM_MODES]; code_points.len()];

    // At the beginning of each iteration of the loop below,
    // prevCosts[j] is the exact minimum number of 1/6 bits needed to encode the entire string prefix of length i, and end in modeTypes[j]
    let mut prev_costs = head_costs.clone();

    // Calculate costs using dynamic programming
    for i in 0..code_points.len() {
        let c = code_points[i];
        let mut cur_costs = [0usize; NUM_MODES];

        {
            // Always extend a byte mode segment
            cur_costs[0] = prev_costs[0] + c.len_utf8() * 8 * 6;
            char_modes[i][0] = Some(MODE_TYPES[0]);
        }

        // Extend a segment if possible
        if ALPHANUMERIC_CHARSET.contains(&c) { // Is alphanumeric
            cur_costs[1] = prev_costs[1] + 33; // 5.5 bits per alphanumeric char
            char_modes[i][1] = Some(MODE_TYPES[1]);
        }
        if '0' <= c && c <= '9' { // Is numeric
            cur_costs[2] = prev_costs[2] + 20; // 3.33 bits per digit
            char_modes[i][2] = Some(MODE_TYPES[2]);
        }
        if cfg!(feature = "kanji") {
            if is_kanji(c) {
                cur_costs[3] = prev_costs[3] + 78; // 13 bits per Shift JIS char
                char_modes[i][3] = Some(MODE_TYPES[3]);
            }
        }

        // Start new segment at the end to switch modes
        for j in 0..NUM_MODES { // To mode
            for k in 0..NUM_MODES { // From mode
                let new_cost = (cur_costs[k] + 5) / 6 * 6 + head_costs[j];
                if char_modes[i][k].is_some() && (char_modes[i][j].is_none() || new_cost < cur_costs[j]) {
                    cur_costs[j] = new_cost;
                    char_modes[i][j] = Some(MODE_TYPES[k]);
                }
            }
        }

        prev_costs = cur_costs;
    }

    // Find optimal ending mode
    let mut cur_mode = None::<QrSegmentMode>;

    let mut min_cost = 0;

    for i in 0..NUM_MODES {
        if cur_mode.is_none() || prev_costs[i] < min_cost {
            min_cost = prev_costs[i];
            cur_mode = Some(MODE_TYPES[i]);
        }
    }

    let mut cur_mode = cur_mode.unwrap();

    let mut result = vec![QrSegmentMode::Byte; char_modes.len()];

    // Get optimal mode for each code point by tracing backwards
    for i in (0..char_modes.len()).rev() {
        for j in 0..NUM_MODES {
            if MODE_TYPES[j] == cur_mode {
                cur_mode = char_modes[i][j].unwrap();
                result[i] = cur_mode;
                break;
            }
        }
    }

    result
}

// Returns a new list of segments based on the given text and modes, such that consecutive code points in the same mode are put into the same segment.
fn split_into_segments(code_points: &[char], char_modes: &[QrSegmentMode]) -> Vec<QrSegment> {
    let mut result = Vec::new();

    // Accumulate run of modes
    let mut cur_mode = char_modes[0];

    let mut start = 0;

    let mut i = 0;
    loop {
        i += 1;

        if i < code_points.len() && char_modes[i] == cur_mode {
            continue;
        }

        let s = &code_points[start..i];

        match cur_mode {
            QrSegmentMode::Byte => {
                let s: String = s.iter().collect();
                let v = s.into_bytes();
                result.push(QrSegment::make_bytes(&v));
            }
            QrSegmentMode::Numeric => {
                result.push(QrSegment::make_numeric(s));
            }
            QrSegmentMode::Alphanumeric => {
                result.push(QrSegment::make_alphanumeric(s));
            }
            QrSegmentMode::Kanji => {
                if cfg!(feature = "kanji") {
                    result.push(make_kanji(s));
                } else {
                    unreachable!()
                }
            }
            _ => unreachable!()
        }

        if i >= code_points.len() {
            return result;
        }

        cur_mode = char_modes[i];
        start = i;
    }
}

/*---- Kanji mode segment encoder ----*/

#[cfg(feature = "kanji")]
/// Returns a segment representing the specified text string encoded in kanji mode.
pub fn make_kanji(code_points: &[char]) -> QrSegment {
    let mut bb = BitBuffer(Vec::new());

    for &c in code_points {
        let val = UNICODE_TO_QR_KANJI[c as usize];

        if val == -1 {
            panic!("String contains non-kanji-mode characters");
        }

        bb.append_bits(val as u32, 13);
    }

    QrSegment::new(QrSegmentMode::Kanji, code_points.len(), bb.0)
}

#[cfg(not(feature = "kanji"))]
fn make_kanji(_: &[char]) -> QrSegment {
    unreachable!()
}

#[cfg(feature = "kanji")]
/// Tests whether the specified string can be encoded as a segment in kanji mode.
pub fn is_encodable_as_kanji(code_points: &[char]) -> bool {
    for &c in code_points {
        if !is_kanji(c) {
            return false;
        }
    }
    true
}

#[cfg(feature = "kanji")]
pub fn is_kanji(c: char) -> bool {
    let c = c as usize;
    c < UNICODE_TO_QR_KANJI.len() && UNICODE_TO_QR_KANJI[c] != -1
}

#[cfg(not(feature = "kanji"))]
fn is_kanji(_: char) -> bool {
    unreachable!()
}

#[cfg(feature = "kanji")]
// Load the unpacked the computation-friendly Shift JIS table
static UNICODE_TO_QR_KANJI: [i16; 1 << 16] = include!("unicode_to_qr_kanji.json");