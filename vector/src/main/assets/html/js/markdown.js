$(function() {
    marked.setOptions({
        langPrefix: '',
		gfm: true,
		tables: true,
		breaks: true,
		pedantic: false,
		sanitize: true,
		smartLists: true,
		smartypants: false,
    });
	
    convertToHtml = function setMarkdown(md_text){
        if(md_text == "") return false;
        md_text = md_text.replace(/\\n/g, "\n");
        var md_html = marked(md_text);
		Android.wOnParse(md_html);
    };
});
