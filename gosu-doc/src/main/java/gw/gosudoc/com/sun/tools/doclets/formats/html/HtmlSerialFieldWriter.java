/*
 * This file is a shadowed version of the older javadoc codebase on which gosudoc is based; borrowed from jdk 9.
 */

package gw.gosudoc.com.sun.tools.doclets.formats.html;

import java.util.*;




import gw.gosudoc.com.sun.javadoc.SerialFieldTag;
import gw.gosudoc.com.sun.tools.doclets.formats.html.markup.*;
import gw.gosudoc.com.sun.tools.doclets.internal.toolkit.Content;
import gw.gosudoc.com.sun.tools.doclets.internal.toolkit.SerializedFormWriter;
import gw.gosudoc.com.sun.tools.doclets.internal.toolkit.taglets.TagletWriter;

/**
 * Generate serialized form for serializable fields.
 * Documentation denoted by the tags <code>serial</code> and
 * <code>serialField</code> is processed.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Joe Fialli
 * @author Bhavesh Patel (Modified)
 */
@Deprecated
public class HtmlSerialFieldWriter extends FieldWriterImpl
        implements SerializedFormWriter.SerialFieldWriter {
    gw.gosudoc.com.sun.javadoc.ProgramElementDoc[] members = null;

    public HtmlSerialFieldWriter(SubWriterHolderWriter writer,
                                    gw.gosudoc.com.sun.javadoc.ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public List<gw.gosudoc.com.sun.javadoc.FieldDoc> members( gw.gosudoc.com.sun.javadoc.ClassDoc cd) {
        return Arrays.asList(cd.serializableFields());
    }

    /**
     * Return the header for serializable fields section.
     *
     * @return a content tree for the header
     */
    public Content getSerializableFieldsHeader() {
        HtmlTree ul = new HtmlTree( HtmlTag.UL);
        ul.addStyle( HtmlStyle.blockList);
        return ul;
    }

    /**
     * Return the header for serializable fields content section.
     *
     * @param isLastContent true if the cotent being documented is the last content.
     * @return a content tree for the header
     */
    public Content getFieldsContentHeader(boolean isLastContent) {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        if (isLastContent)
            li.addStyle(HtmlStyle.blockListLast);
        else
            li.addStyle(HtmlStyle.blockList);
        return li;
    }

    /**
     * Add serializable fields.
     *
     * @param heading the heading for the section
     * @param serializableFieldsTree the tree to be added to the serializable fileds
     *        content tree
     * @return a content tree for the serializable fields content
     */
    public Content getSerializableFields(String heading, Content serializableFieldsTree) {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        li.addStyle(HtmlStyle.blockList);
        if (serializableFieldsTree.isValid()) {
            Content headingContent = new StringContent(heading);
            Content serialHeading = HtmlTree.HEADING( HtmlConstants.SERIALIZED_MEMBER_HEADING,
                    headingContent);
            li.addContent(serialHeading);
            li.addContent(serializableFieldsTree);
        }
        return li;
    }

    /**
     * Add the member header.
     *
     * @param fieldType the class document to be listed
     * @param fieldTypeStr the string for the field type to be documented
     * @param fieldDimensions the dimensions of the field string to be added
     * @param fieldName name of the field to be added
     * @param contentTree the content tree to which the member header will be added
     */
    public void addMemberHeader( gw.gosudoc.com.sun.javadoc.ClassDoc fieldType, String fieldTypeStr,
                                 String fieldDimensions, String fieldName, Content contentTree) {
        Content nameContent = new RawHtml(fieldName);
        Content heading = HtmlTree.HEADING(HtmlConstants.MEMBER_HEADING, nameContent);
        contentTree.addContent(heading);
        Content pre = new HtmlTree(HtmlTag.PRE);
        if (fieldType == null) {
            pre.addContent(fieldTypeStr);
        } else {
            Content fieldContent = writer.getLink(new LinkInfoImpl(
                    configuration, LinkInfoImpl.Kind.SERIAL_MEMBER, fieldType));
            pre.addContent(fieldContent);
        }
        pre.addContent(fieldDimensions + " ");
        pre.addContent(fieldName);
        contentTree.addContent(pre);
    }

    /**
     * Add the deprecated information for this member.
     *
     * @param field the field to document.
     * @param contentTree the tree to which the deprecated info will be added
     */
    public void addMemberDeprecatedInfo( gw.gosudoc.com.sun.javadoc.FieldDoc field, Content contentTree) {
        addDeprecatedInfo(field, contentTree);
    }

    /**
     * Add the description text for this member.
     *
     * @param field the field to document.
     * @param contentTree the tree to which the deprecated info will be added
     */
    public void addMemberDescription( gw.gosudoc.com.sun.javadoc.FieldDoc field, Content contentTree) {
        if (field.inlineTags().length > 0) {
            writer.addInlineComment(field, contentTree);
        }
        gw.gosudoc.com.sun.javadoc.Tag[] tags = field.tags("serial");
        if (tags.length > 0) {
            writer.addInlineComment(field, tags[0], contentTree);
        }
    }

    /**
     * Add the description text for this member represented by the tag.
     *
     * @param serialFieldTag the field to document (represented by tag)
     * @param contentTree the tree to which the deprecated info will be added
     */
    public void addMemberDescription( SerialFieldTag serialFieldTag, Content contentTree) {
        String serialFieldTagDesc = serialFieldTag.description().trim();
        if (!serialFieldTagDesc.isEmpty()) {
            Content serialFieldContent = new RawHtml(serialFieldTagDesc);
            Content div = HtmlTree.DIV(HtmlStyle.block, serialFieldContent);
            contentTree.addContent(div);
        }
    }

    /**
     * Add the tag information for this member.
     *
     * @param field the field to document.
     * @param contentTree the tree to which the member tags info will be added
     */
    public void addMemberTags( gw.gosudoc.com.sun.javadoc.FieldDoc field, Content contentTree) {
        Content tagContent = new ContentBuilder();
        TagletWriter.genTagOuput(configuration.tagletManager, field,
                configuration.tagletManager.getCustomTaglets(field),
                writer.getTagletWriterInstance(false), tagContent);
        Content dlTags = new HtmlTree(HtmlTag.DL);
        dlTags.addContent(tagContent);
        contentTree.addContent(dlTags);  // TODO: what if empty?
    }

    /**
     * Check to see if overview details should be printed. If
     * nocomment option set or if there is no text to be printed
     * for deprecation info, comment or tags, do not print overview details.
     *
     * @param field the field to check overview details for.
     * @return true if overview details need to be printed
     */
    public boolean shouldPrintOverview( gw.gosudoc.com.sun.javadoc.FieldDoc field) {
        if (!configuration.nocomment) {
            if(!field.commentText().isEmpty() ||
                    writer.hasSerializationOverviewTags(field))
                return true;
        }
        if (field.tags("deprecated").length > 0)
            return true;
        return false;
    }
}