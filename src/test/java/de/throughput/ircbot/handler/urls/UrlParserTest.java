package de.throughput.ircbot.handler.urls;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class UrlParserTest {

    // some random URLs
    private static final List<String> URLS = List.of(
            "https://www.heise.de/newsticker/meldung/Corona-Pandemie-Ueber-Abwassertests-zur-tatsaechlichen-Virusverbreitung-4701339.html",
            "https://www.spiegel.de/politik/ausland/wuhan-in-china-eine-stadt-erwacht-aus-dem-koma-a-00000000-0002-0001-0000-000170435635",
            "https://www.aliexpress.com/wholesale?catId=0&initiative_id=SB_20200411170700&SearchText=n95+masks",
            "https://www.who.int/emergencies/diseases/novel-coronavirus-2019",
            "ftp://mirrors.edge.kernel.org/pub/linux/kernel/",
            "https://www.nursing.virginia.edu/centers-initiatives/center-for-appreciative-practice/covid-19/covid-19-haikus/",
            "https://www.aliexpress.com/item/4000846454266.html?spm=a2g0o.productlist.0.0.e2b770cfd7gwYT&"
                    + "algo_pvid=694c8bd4-8d3d-42aa-930f-622a4d7554e7&algo_expid=694c8bd4-8d3d-42aa-930f-622a4d7554e7-8&btsid=0be3743b15866540687246566e5b0b&"
                    + "ws_ab_test=searchweb0_0,searchweb201602_,searchweb201603_",
            "http://redir.to/?url=https://www.heise.de/"
    );

    // source: https://www.nursing.virginia.edu/centers-initiatives/center-for-appreciative-practice/covid-19/covid-19-haikus/
    private static final List<String> TEXT = List.of(
            "Mind full, not mindful",
            "Racing through COVID clutter",
            "Be still, focus, breathe, connect",

            "Did you notice clouds?",
            "Did you hear the birds singing?",
            "Quiet is a gift."
    );

    @Test
    public void testStreamUrls() {
        String text = text();

        List<String> urls = UrlParser.streamUrls(text)
                .collect(Collectors.toList());

        assertEquals(urls, URLS);
    }

    private static String text() {
        StringBuilder sb = new StringBuilder();
        Iterator<String> textIter = TEXT.iterator();
        for (String url : URLS) {
            if (!textIter.hasNext()) {
                textIter = TEXT.iterator();
            }
            sb.append(textIter.next())
                    .append(" ");
            sb.append(url)
                    .append(" ");
        }
        if (!textIter.hasNext()) {
            textIter = TEXT.iterator();
        }
        sb.append(textIter.next());
        return sb.toString();
    }

}
