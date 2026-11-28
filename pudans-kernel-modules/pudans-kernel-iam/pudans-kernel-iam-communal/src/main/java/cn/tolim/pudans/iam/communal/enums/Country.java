package cn.tolim.pudans.iam.communal.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum Country implements BaseEnum {
    // Andorra
    AD("AD", "Andorra", "安道尔"),
    // Austria
    AT("AT", "Austria", "奥地利"),
    // Australia
    AU("AU", "Australia", "澳大利亚"),
    // Albania
    AL("AL", "Albania", "阿尔巴尼亚"),
    // Algeria
    DZ("DZ", "Algeria", "阿尔及利亚"),
    // Ireland
    IE("IE", "Ireland", "爱尔兰"),

    // Afghanistan
    AF("AF", "Afghanistan", "阿富汗"),

    // Anguilla
    AI("AI", "Anguilla", "安圭拉"),
    // Angola
    AO("AO", "Angola", "安哥拉"),
    // Argentina
    AR("AR", "Argentina", "阿根廷"),
    // Egypt
    EG("EG", "Egypt", "埃及"),
    // Aruba
    AW("AW", "Aruba", "阿鲁巴"),

    // United Arab Emirates
    AE("AE", "United Arab Emirates", "阿拉伯联合酋长国"),
    // Oman
    OM("OM", "Oman", "阿曼"),
    // Azerbaijan
    AZ("AZ", "Azerbaijan", "阿塞拜疆"),
    // Ethiopia
    ET("ET", "Ethiopia", "埃塞俄比亚"),
    // Estonia
    EE("EE", "Estonia", "爱沙尼亚"),
    // Antigua and Barbuda
    AG("AG", "Antigua and Barbuda", "安提瓜岛和巴布达"),
    // Barbados
    BB("BB", "Barbados", "巴巴多斯岛"),
    // Papua New Guinea
    PG("PG", "Papua New Guinea", "巴布亚新几内亚"),
    // Botswana
    BW("BW", "Botswana", "博茨瓦纳"),
    // Iceland
    IS("IS", "Iceland", "冰岛"),
    // Bhutan
    BT("BT", "Bhutan", "不丹"),
    // Puerto Rico
    PR("PR", "Puerto Rico", "波多黎各"),
    // Belarus
    BY("BY", "Belarus", "白俄罗斯"),
    // Bahamas
    BS("BS", "Bahamas", "巴哈马"),
    // Bulgaria
    BG("BG", "Bulgaria", "保加利亚"),
    // Burkina Faso
    BF("BF", "Burkina Faso", "布基纳法索"),
    // Pakistan

    PK("PK", "Pakistan", "巴基斯坦"),
    // Poland
    PL("PL", "Poland", "波兰"),
    // Bahrain
    BH("BH", "Bahrain", "巴林"),
    // Burundi
    BI("BI", "Burundi", "布隆迪"),
    // Paraguay
    PY("PY", "Paraguay", "巴拉圭"),
    // Belgium
    BE("BE", "Belgium", "比利时"),
    // Palestine
    PS("PS", "Palestine, State of", "巴勒斯坦"),
    // Bolivia
    BO("BO", "Bolivia, Plurinational State of", "玻利维亚"),
    // Belize
    BZ("BZ", "Belize", "伯利兹"),
    // Bermuda
    BM("BM", "Bermuda", "百慕大"),
    // Northern Mariana Islands
    MP("MP", "Northern Mariana Islands", "北马里亚纳群岛"),
    // Benin
    BJ("BJ", "Benin", "贝宁"),
    // Panama
    PA("PA", "Panama", "巴拿马"),
    // Bosnia and Herzegovina
    BA("BA", "Bosnia and Herzegovina", "波斯尼亚和黑塞哥维那"),
    // Bouvet Island
    BV("BV", "Bouvet Island", "布韦岛"),
    // Brazil
    BR("BR", "Brazil", "巴西"),
    // Equatorial Guinea
    GQ("GQ", "Equatorial Guinea", "赤道几内亚"),
    // North Korea
    KP("KP", "Korea, Democratic People's Republic of", "朝鲜"),
    // Timor-Leste
    TL("TL", "Timor-Leste", "东帝汶"),
    // Germany
    DE("DE", "Germany", "德国"),
    // Togo
    TG("TG", "Togo", "多哥"),
    // Vatican City
    VA("VA", "Holy See (Vatican City State)", "梵蒂冈"),
    // Denmark
    DK("DK", "Denmark", "丹麦"),
    // Dominica
    DM("DM", "Dominica", "多米尼加"),
    // Dominican Republic
    DO("DO", "Dominican Republic", "多米尼加共和国"),
    // Ecuador
    EC("EC", "Ecuador", "厄瓜多尔"),
    // Russia
    RU("RU", "Russian Federation", "俄罗斯"),
    // Eritrea
    ER("ER", "Eritrea", "厄立特里亚"),
    // Cape Verde
    CV("CV", "Cape Verde", "佛得角"),
    // France
    FR("FR", "France", "法国"),
    // Finland
    FI("FI", "Finland", "芬兰"),
    // Philippines
    PH("PH", "Philippines", "菲律宾"),
    // Clipperton Island
    FK("FK", "Falkland Islands (Malvinas)", "弗兰克群岛"),
    // Faroe Islands
    FO("FO", "Faroe Islands", "法罗群岛"),
    // French Polynesia
    PF("PF", "French Polynesia", "法属波利尼西亚"),
    // French Guiana
    GF("GF", "French Guiana", "法属圭亚那"),
    // French Southern Territories
    TF("TF", "French Southern Territories", "法属南部领地"),
    // Cuba
    CU("CU", "Cuba", "古巴"),
    // Gambia
    GM("GM", "Gambia", "冈比亚"),
    // Guam
    GU("GU", "Guam", "关岛"),
    // Guadeloupe
    GP("GP", "Guadeloupe", "瓜德罗普岛"),
    // Congo
    CG("CG", "Congo", "刚果"),
    // Democratic Republic of the Congo
    CD("CD", "Congo, the Democratic Republic of the", "刚果（金）"),
    // Colombia
    CO("CO", "Colombia", "哥伦比亚"),
    // Georgia
    GE("GE", "Georgia", "格鲁吉亚"),
    // Greenland
    GL("GL", "Greenland", "格陵兰"),
    // Grenada
    GD("GD", "Grenada", "格林纳达"),
    // Costa Rica
    CR("CR", "Costa Rica", "哥斯达黎加"),
    // Guernsey
    GG("GG", "Guernsey", "根西岛"),
    // Guyana
    GY("GY", "Guyana", "圭亚那"),
    // Haiti
    HT("HT", "Haiti", "海地"),
    // Heard Island and McDonald Islands
    HM("HM", "Heard Island and McDonald Islands", "赫德和麦克唐纳群岛"),
    // Honduras
    HN("HN", "Honduras", "洪都拉斯"),
    // South Korea
    KR("KR", "Korea, Republic of", "韩国"),
    // Netherlands
    NL("NL", "Netherlands", "荷兰"),
    // Montenegro
    ME("ME", "Montenegro", "黑山"),
    // Kazakhstan
    KZ("KZ", "Kazakhstan", "哈萨克斯坦"),
    // Fiji
    FJ("FJ", "Fiji", "斐济"),
    // Zimbabwe
    ZW("ZW", "Zimbabwe", "津巴布韦"),
    // Djibouti
    DJ("DJ", "Djibouti", "吉布提"),
    // Kyrgyzstan
    KG("KG", "Kyrgyzstan", "吉尔吉斯斯坦"),
    // Czechia
    CZ("CZ", "Czech Republic", "捷克"),
    // Kiribati
    KI("KI", "Kiribati", "基里巴斯"),
    // Ghana
    GH("GH", "Ghana", "加纳"),
    // Canada
    CA("CA", "Canada", "加拿大"),
    // Guinea
    GN("GN", "Guinea", "几内亚"),
    // Guinea-Bissau
    GW("GW", "Guinea-Bissau", "几内亚比绍"),
    // Gabon
    GA("GA", "Gabon", "加蓬"),
    // Cambodia
    KH("KH", "Cambodia", "柬埔寨"),
    // Cook Islands
    CK("CK", "Cook Islands", "库克群岛"),
    // Cocos Islands
    CC("CC", "Cocos (Keeling) Islands", "科科斯群岛"),
    // Croatia
    HR("HR", "Croatia", "克罗地亚"),
    // Comoros
    KM("KM", "Comoros", "科摩罗"),
    // Cameroon
    CM("CM", "Cameroon", "喀麦隆"),
    // Cayman Islands
    KY("KY", "Cayman Islands", "开曼群岛"),
    // Kenya
    KE("KE", "Kenya", "肯尼亚"),
    // Qatar
    QA("QA", "Qatar", "卡塔尔"),
    // Kuwait
    KW("KW", "Kuwait", "科威特"),
    // Nauru
    NR("NR", "Nauru", "瑙鲁"),
    // Liberia
    LR("LR", "Liberia", "利比里亚"),
    // Lebanon
    LB("LB", "Lebanon", "黎巴嫩"),
    // Libya
    LY("LY", "Libya", "利比亚"),
    // Romania
    RO("RO", "Romania", "罗马尼亚"),
    // Luxembourg
    LU("LU", "Luxembourg", "卢森堡"),
    // Lesotho
    LS("LS", "Lesotho", "莱索托"),
    // Lithuania
    LT("LT", "Lithuania", "立陶宛"),
    // Latvia
    LV("LV", "Latvia", "拉脱维亚"),
    // Laos
    LA("LA", "Lao People's Democratic Republic", "老挝"),
    // Rwanda
    RW("RW", "Rwanda", "卢旺达"),
    // Liechtenstein
    LI("LI", "Liechtenstein", "列支敦士登"),
    // Myanmar
    MM("MM", "Myanmar", "缅甸"),
    // Madagascar
    MG("MG", "Madagascar", "马达加斯加"),
    // Isle of Man
    IM("IM", "Isle of Man", "马恩岛"),
    // Maldives
    MV("MV", "Maldives", "马尔代夫"),
    // Moldova
    MD("MD", "Moldova, Republic of", "摩尔多瓦"),
    // Malta
    MT("MT", "Malta", "马耳他"),
    // United States
    US("US", "United States", "美国"),
    // Mongolia
    MN("MN", "Mongolia", "蒙古"),
    // Bangladesh
    BD("BD", "Bangladesh", "孟加拉"),
    // Micronesia
    FM("FM", "Micronesia, Federated States of", "密克罗尼西亚"),
    // Peru
    PE("PE", "Peru", "秘鲁"),
    // Mali
    ML("ML", "Mali", "马里"),
    // Morocco
    MA("MA", "Morocco", "摩洛哥"),
    // Mauritius
    MU("MU", "Mauritius", "毛里求斯"),
    // Mauritania
    MR("MR", "Mauritania", "毛里塔尼亚"),
    // Malawi
    MW("MW", "Malawi", "马拉维"),
    // Malaysia
    MY("MY", "Malaysia", "马来西亚"),
    // Monaco
    MC("MC", "Monaco", "摩纳哥"),
    // North Macedonia
    MK("MK", "Macedonia, the Former Yugoslav Republic of",
            "马其顿"),
    // Mozambique
    MZ("MZ", "Mozambique", "莫桑比克"),
    // Marshall Islands
    MH("MH", "Marshall Islands", "马绍尔群岛"),
    // American Samoa
    AS("AS", "American Samoa", "美属萨摩亚"),
    // Martinique
    MQ("MQ", "Martinique", "马提尼克岛"),
    // Montserrat
    MS("MS", "Montserrat", "蒙特塞拉特"),
    // Mexico
    MX("MX", "Mexico", "墨西哥"),
    // Mayotte
    YT("YT", "Mayotte", "马约特"),
    // Niue
    NU("NU", "Niue", "纽埃"),
    // Nepal
    NP("NP", "Nepal", "尼泊尔"),
    // South Africa
    ZA("ZA", "South Africa", "南非"),
    // Norfolk Island
    NF("NF", "Norfolk Island", "诺福克岛"),
    // Nicaragua
    NI("NI", "Nicaragua", "尼加拉瓜"),

    NA("NA", "Namibia", "纳米比亚"),
    // South Georgia and South Sandwich Islands
    GS("GS", "South Georgia and the South Sandwich Islands",
            "南乔治亚和南桑德威奇群岛"),
    // Niger
    NE("NE", "Niger", "尼日尔"),
    // Nigeria
    NG("NG", "Nigeria", "尼日利亚"),
    // South Sudan
    SS("SS", "South Sudan", "南苏丹"),
    // Norway
    NO("NO", "Norway", "挪威"),
    // Palau
    PW("PW", "Palau", "帕劳"),
    // Pitcairn
    PN("PN", "Pitcairn", "皮特凯恩"),
    // Portugal
    PT("PT", "Portugal", "葡萄牙"),
    // Japan
    JP("JP", "Japan", "日本"),
    // Sweden
    SE("SE", "Sweden", "瑞典"),
    // Switzerland
    CH("CH", "Switzerland", "瑞士"),
    // Sudan
    SD("SD", "Sudan", "苏丹"),
    // Christmas Island
    CX("CX", "Christmas Island", "圣诞岛"),
    // Sao Tome and Principe
    ST("ST", "Sao Tome and Principe", "圣多美和普林西比"),
    // El Salvador
    SV("SV", "El Salvador", "萨尔瓦多"),
    // Serbia
    RS("RS", "Serbia", "塞尔维亚"),
    // Saint Helena
    SH("SH", "Saint Helena, Ascension and Tristan da Cunha",
            "圣赫勒拿"),
    // Saint Kitts and Nevis
    KN("KN", "Saint Kitts and Nevis", "圣基茨和尼维斯"),
    // Slovakia
    SK("SK", "Slovakia", "斯洛伐克"),
    // Sierra Leone
    SL("SL", "Sierra Leone", "塞拉利昂"),
    // Sri Lanka
    LK("LK", "Sri Lanka", "斯里兰卡"),
    // Solomon Islands
    SB("SB", "Solomon Islands", "所罗门群岛"),
    // Suriname
    SR("SR", "Suriname", "苏里南"),
    // Slovenia
    SI("SI", "Slovenia", "斯洛文尼亚"),
    // Saint Lucia
    LC("LC", "Saint Lucia", "圣卢西亚"),
    // Somalia
    SO("SO", "Somalia", "索马里"),
    // San Marino
    SM("SM", "San Marino", "圣马力诺"),
    // Samoa
    WS("WS", "Samoa", "萨摩亚"),
    // Senegal
    SN("SN", "Senegal", "塞内加尔"),
    // Saint Pierre and Miquelon
    PM("PM", "Saint Pierre and Miquelon", "圣皮埃尔和米克隆群岛"),
    // Cyprus
    CY("CY", "Cyprus", "塞浦路斯"),
    // Seychelles
    SC("SC", "Seychelles", "塞舌尔"),
    // Saudi Arabia
    SA("SA", "Saudi Arabia", "沙特阿拉伯"),
    // Svalbard and Jan Mayen
    SJ("SJ", "Svalbard and Jan Mayen", "斯瓦尔巴和扬马廷"),
    // Eswatini
    SZ("SZ", "Swaziland", "斯威士兰"),
    // Saint Vincent and the Grenadines
    VC("VC", "Saint Vincent and the Grenadines", "圣文森特和格林纳丁斯"),
    // Turkey
    TR("TR", "Turkey", "土耳其"),
    // Thailand
    TH("TH", "Thailand", "泰国"),
    // Tonga
    TO("TO", "Tonga", "汤加"),
    // Tajikistan
    TJ("TJ", "Tajikistan", "塔吉克斯坦"),
    // Tokelau
    TK("TK", "Tokelau", "托克劳"),
    // Turkmenistan
    TM("TM", "Turkmenistan", "土库曼斯坦"),
    // Turks and Caicos Islands
    TC("TC", "Turks and Caicos Islands", "特克斯和凯克特斯群岛"),
    // Trinidad and Tobago
    TT("TT", "Trinidad and Tobago", "特立尼达和多巴哥"),
    // Tunisia
    TN("TN", "Tunisia", "突尼斯"),
    // Tanzania
    TZ("TZ", "Tanzania, United Republic of", "坦桑尼亚"),
    // Tuvalu
    TV("TV", "Tuvalu", "图瓦卢"),
    // Guatemala
    GT("GT", "Guatemala", "危地马拉"),
    // British Virgin Islands
    VG("VG", "Virgin Islands, British", "维尔京群岛，英属"),
    // U.S. Virgin Islands
    VI("VI", "Virgin Islands, U.S.", "维尔京群岛，美属"),
    // Uganda
    UG("UG", "Uganda", "乌干达"),
    // Ukraine
    UA("UA", "Ukraine", "乌克兰"),
    // Brunei
    BN("BN", "Brunei Darussalam", "文莱"),
    // Uruguay
    UY("UY", "Uruguay", "乌拉圭"),
    // Wallis and Futuna
    WF("WF", "Wallis and Futuna", "瓦利斯和福图纳"),
    // Vanuatu
    VU("VU", "Vanuatu", "瓦努阿图"),
    // Venezuela
    VE("VE", "Venezuela, Bolivarian Republic of", "委内瑞拉"),
    // Uzbekistan
    UZ("UZ", "Uzbekistan", "乌兹别克斯坦"),
    // Spain
    ES("ES", "Spain", "西班牙"),
    // Singapore
    SG("SG", "Singapore", "新加坡"),
    // New Caledonia
    NC("NC", "New Caledonia", "新喀里多尼亚"),
    // Greece
    GR("GR", "Greece", "希腊"),
    // Syria
    SY("SY", "Syrian Arab Republic", "叙利亚"),
    // Western Sahara
    EH("EH", "Western Sahara", "西撒哈拉"),
    // New Zealand
    NZ("NZ", "New Zealand", "新西兰"),
    // Hungary
    HU("HU", "Hungary", "匈牙利"),
    // Jordan
    JO("JO", "Jordan", "约旦"),
    // India
    IN("IN", "India", "印度"),
    // Italy
    IT("IT", "Italy", "意大利"),
    // Indonesia
    ID("ID", "Indonesia", "印度尼西亚"),
    // United Kingdom
    GB("GB", "United Kingdom", "英国"),
    // Iran
    IR("IR", "Iran, Islamic Republic of", "伊朗"),
    // Iraq
    IQ("IQ", "Iraq", "伊拉克"),
    // Yemen
    YE("YE", "Yemen", "也门"),
    // Jamaica
    JM("JM", "Jamaica", "牙买加"),
    // Armenia
    AM("AM", "Armenia", "亚美尼亚"),
    // Vietnam
    VN("VN", "Viet Nam", "越南"),
    // Israel
    IL("IL", "Israel", "以色列"),
    // British Indian Ocean Territory
    IO("IO", "British Indian Ocean Territory", "英属印度洋领地"),
    // Gibraltar
    GI("GI", "Gibraltar", "直布罗陀"),
    // Zambia
    ZM("ZM", "Zambia", "赞比亚"),
    // Chad
    TD("TD", "Chad", "乍得"),
    // Central African Republic
    CF("CF", "Central African Republic", "中非"),
    // China
    CN("CN", "China", "中国"),
    // Macao, China
    MO("MO", "Macao", "中国澳门"),
    // Taiwan, China
    TW("TW", "Taiwan, Province of China", "中国台湾"),
    // Hong Kong, China
    HK("HK", "Hong Kong", "中国香港"),
    // Chile
    CL("CL", "Chile", "智利"),
    // Jersey
    JE("JE", "Jersey", "泽西岛"),
    // Bonaire, Sint Eustatius and Saba
    BQ("BQ", "Bonaire, Sint Eustatius and Saba",
            "博内尔岛、圣尤斯特歇斯岛和萨巴岛"),
    // U.S. Minor Outlying Islands
    UM("UM", "United States Minor Outlying Islands (the)",
            "美国本土外小岛屿 (the)"),
    // Ivory Coast
    CI("CI", "Côte d'Ivoire", "象牙海岸");

    @JsonValue
    private final String code;
    private final String name;
    private final String chineseAbbreviation;

    Country(String code, String name, String chineseAbbreviation) {
        this.code = code;
        this.name = name;
        this.chineseAbbreviation = chineseAbbreviation;
    }

    @EnumConvert
    public static Country getCountry(String code) {
        Country[] values = values();
        for (Country country : values) {
            if (String.valueOf(country.getCode()).equals(code)) {
                return country;
            }
        }
        return null;
    }

    public static Country getByCountryName(String name) {
        Country[] values = values();
        for (Country country : values) {
            if (String.valueOf(country.getName()).equals(name)) {
                return country;
            }
        }
        return null;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return name;
    }

    public String getName() {
        return name;
    }

    public String getChineseAbbreviation() {
        return chineseAbbreviation;
    }
}
