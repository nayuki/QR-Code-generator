/* 
 * QR Code generator library (.NET)
 * 
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */

using System.Collections.Generic;
using Xunit;

namespace IO.Nayuki.QrCodeGen.Test
{
    public class KanjiTest
    {
        private const string KanjiSample
            = "委マツニオ男喜でろつ絶選にッ自君エ碁４配堪イルネコ禁２京果ユウタカ著書ンぴは役村ン政経購税き視思６１近退ほぜ。投むでみ書権し輔組キ黒壁えちをあ刊中申内イヌ著太記ヲ庁宿ホマ湯連マクラ相就づ藤業トリカ場止ラマエニ現露ラ情伝ま登厘架れち。任ムオホ除８５郊ツ執父たはんゅ喜離レクセサ社暖せげ磨育こぽずぎ聞来ンは事聞煙や無杯すやべぽ信９３幕もいび及町いば事階齢利江ず。"
              + "陸スシネク賢軽ワマ産闘人めせ視豊ネワマキ庭朝療へ終胸ずでリぐ価交こる覧夜でわみを氷性聞年よとク。朝定掲ぞぴえる縄策ヌキウク主力まひ本生ウヒア鎖検ヘ同階リぞ美字ツイキニ問企表カクツ助１台づレぼえ。事箕ケテ内上べが訃長す容批ヨ多帯ヌムフハ社早オイ球選アヘ話交じぞぽ上国７８覚は根辺むげたょ室也も。"
              + "数ヨフヱイ血結質ッろ護外すうだご古犠リ自陽カアタ幅交数ぼもそき請件ろ問拉著フヒミ陰天６１債ン。竹ごつべク市成キヤウ検新スセヒヲ花期的もぴは嶋信ヱマサワ局記来イヲナネ比引フタニハ津適よいうり長熊う割監ッそ投出リラツル誇載距ふかすん論紅ょごくむ井５消宗征訟おなス。油ヤレワ減力テミ機４６残ツケ遺鳥そ前名ニ作３８草あぶづだ見遠ヘ覧程ラり転由教おとへ当潟とみ味購域施活ぜどだ。"
              + "駆投ねづ女日べリりが離口にわ神新え続竹ワハ時円とりば権読け質打ノセ記囲ラヨヒソ暮交だ族時加せげ。山テカ牧於ちぶん社４７開低きねぽぶ眼明んドっ東検キ連可劇はよしぶ意田ソ信道ユヌネル経自い日花ぴラに。休ク況術ハヤ感要ヨ禁社ぴくざな賠表台里レヱ属自ホヱ民活え因物ぴぽド形型会ロモヱセ法本談ル関４９打ツノ速新ぎき堀冬密浦絶どぞつ。"
              + "追ろ役陸モヘセ担妊ぱゅン島４７自ミリシホ子載科ぐばのド返物ナ月営わを屋界ヒクエム取強あるゆ売増ロムネ鶏交応更ょ。認投か者木ッ変甲テケツマ類読けっイり鋼巻ス観市徴ンぎる金谷ゆけ当披トソロ薬身とを勢米ハ件確ー実市浮絵レ。職ウト基交と止士高こてゆべ集団制ラクイ観経回スミネカ治６聖うぜ頑機ずお国２０覧歌ヌレ故携ムネナシ選文ヲレ西納３０図ねリず行格ぜお電医丸族の。"
              + "県梨フアヨ長気チ手過フエ本患イやぼ回劇ぶ表神サヘヌ聞変モチ導産埼もすけん付必ッ罪真え号情６長ドンたじ雄員将格真復ほるおと。答モ闘貨午マソカチ革覇ヱキチマ農多ルメタ宮門ルメユ中作ツキテ主書タカモサ事崎ぞルぼ犯柴海図ほち義暴よッろン阪制エ景修級ぐル。変ム注止まどけふ境趣んかラ内経むがる面２８湯ぱはげど元使ナ屋俸どだび会表連トスなり年埋たれゃ。"
              + "資むぎ方退キソセ逃於ケイヲカ域元まど月経快美りか再入ソエヨム播大マス勝星むま芸投フサル男記クかふば詰当ソ患京へ駆食えぞ知属ヱレアユ市秋ょ。７速コ覧広ヤミチケ接済なクろ件２段ト折６７的了だぞじ伸属ぴっ秋頭イナタフ聞践周は上並あ一６便苦レざ外喰杜椿榎び。応めにえン稿業タキリ盗１６水春めわ雑成めす国含ウレハラ最完レ面立更東稿ハクヌ添起チタヨテ営尾ら。"
              + "済メヌ集取ネツホユ人９芋ヒツコワ春３４能メレタツ朝源ぞ名校げイ太化テソタ近慎ナ口２０夜け会報あルトお量問保ぎ場上っ浪足まだゅト育更ラホテカ太健象延ゆル。展つゃた後幸ちぞわぼ金質レ未曲ネマ勝投の若著遅ごぽ時顔セカ報高夫コユエラ応迎飯りばまで愛児ケ息９債核築がち。応文ドッ記強ユノヌエ初検なしよぱ長止表へよ今工テメ相取載ミ青１書にあひ略稿ぶごよ的会報じぐ挑政手一基づ。"
              + "覧ツハ女子み絡康８９嘉塁塾嶋４分４約タニウ政提タヌル王朝よきはべ運案ヒホ男週めーお開培イわ北事巡流む。服勢ラメリユ邦大め地若ゅ構神ヒカロ疑育サ済供しりろべ告惑合コヤリナ代井てじぜゆ沖立験ヲモスロ加知ネ競３０嘉塁塾嶋９０女れフわず勝脱せでに植出党尾滋ず。写ねけを値碁ヌロ計新そへう最男イ筑地ー作提ツムハウ暮工ざ転阜終ふてお抑売買エ題要じレ王護い手乗学カクリソ発府めだごラ変実三以て。"
              + "世なべるそ名走スレヤ変週トウ残予ハサヌカ払者じのドル分定リば米６１与アエユ来熱成かのら終参しつド日備てな要勤奇そむ磐価ヌ問者チ治本ンむくい限前サトスハ郎禁ヘマ自朝せ主並局行承ね。全キロヲ芸来はてし見９所謙レ撤住ワ約子情農スニ手個いよゆぴ事断ニ遊８３常左県煙８東金締ばねぞ。友府ょ盤養虚ぽ昇球つはー繰申チオ境著ごふょ派広だ戦事すめ無問きこどぱ毎保３７火ヱト韓端侍勃卑ぼン。";

        [Fact]
        private void IsKanjiEncodable()
        {
            Assert.True(QrSegmentAdvanced.IsEncodableAsKanji(KanjiSample));
        }


        private const string KanjiText = "昨夜のコンサートは最高でした。";

        private static readonly string[] KanjiModules = {
            "XXXXXXX  XXX   X  XXXXXXX",
            "X     X  X X  X   X     X",
            "X XXX X X X X     X XXX X",
            "X XXX X X   X  XX X XXX X",
            "X XXX X XXX X XXX X XXX X",
            "X     X XXX   XXX X     X",
            "XXXXXXX X X X X X XXXXXXX",
            "        XXXX   X         ",
            "X XXXXX  X    XX  XXXXX  ",
            " X  X  X  XXX X XXXX XXXX",
            "  XX XX XXXX   X  X XXX X",
            "  XX X XXX   X    XX    X",
            "X XX  X   X X     X  X XX",
            "XX  X  XXX   XX X X XX  X",
            "X     XXX XXXX X  XX XX  ",
            "X  XX   XXXX X   X  XXXXX",
            "X XXXXX XX XX   XXXXX  X ",
            "        X X X  XX   XXX X",
            "XXXXXXX  XX X XXX X XX XX",
            "X     X X X XX XX   XX X ",
            "X XXX X X   XXXXXXXXX  X ",
            "X XXX X XXX   X XXX XXX X",
            "X XXX X XXX X X    XX   X",
            "X     X  XX X  XX X  X  X",
            "XXXXXXX X   X  XXXX  XX  "
        };

        [Fact]
        private void KanjiQrCode()
        {
            var segment = QrSegmentAdvanced.MakeKanji(KanjiText);
            var segments = new List<QrSegment> {segment};
            var qrCode = QrCode.EncodeSegments(segments, QrCode.Ecc.Medium);

            Assert.Same(QrCode.Ecc.Medium, qrCode.ErrorCorrectionLevel);
            Assert.Equal(25, qrCode.Size);
            Assert.Equal(2, qrCode.Mask);
            Assert.Equal(KanjiModules, TestHelper.ToStringArray(qrCode));
        }
    }
}
