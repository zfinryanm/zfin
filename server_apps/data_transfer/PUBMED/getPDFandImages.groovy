#!/bin/bash
//usr/bin/env groovy -cp "$GROOVY_CLASSPATH:." "$0" $@; exit $?

import groovy.io.FileType
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.util.slurpersupport.GPathResult
import groovy.xml.StreamingMarkupBuilder

import org.apache.commons.io.FilenameUtils
import org.zfin.properties.ZfinProperties
import org.zfin.properties.ZfinPropertiesEnum

ZfinProperties.init("${System.getenv()['TARGETROOT']}/home/WEB-INF/zfin.properties")

final WORKING_DIR = new File("${ZfinPropertiesEnum.TARGETROOT}/server_apps/data_transfer/PUBMED")
WORKING_DIR.eachFileMatch(~/.*\.txt/) { it.delete() }
WORKING_DIR.eachFileMatch(~/fig.*\.txt/) { it.delete() }
WORKING_DIR.eachFileMatch(~/loadSQL.*\.txt/) { it.delete() }

DBNAME = System.getenv("DBNAME")
PUB_IDS_TO_CHECK = "pdfsNeeded.txt"
PUBS_WITH_PDFS_TO_UPDATE = new File("pdfsAvailable.txt")
FIGS_TO_LOAD = new File("figsToLoad.txt")
PUB_FILES_TO_LOAD = new File("pdfsToLoad.txt")
ADD_BASIC_PDFS_TO_DB = new File("pdfBasicFilesToLoad.txt")
PUBS_TO_GIVE_PERMISSIONS = new File("pubsToGivePermission.txt")

def idsToGrab = [:]

PubmedUtils.psql DBNAME, """
  \\copy (
  SELECT pub_pmc_id, zdb_id
  FROM publication
  WHERE pub_pmc_id IS NOT NULL and pub_pmc_id != ''
     AND NOT EXISTS (SELECT 'x' 
                    FROM publication_file 
                    WHERE pf_pub_zdb_id = zdb_id 
                    AND pf_file_type_id =1)
     AND NOT EXISTS (select 'x' from figure where fig_source_zdb_id = zdb_id) 
     ) to '$PUB_IDS_TO_CHECK' delimiter ',';
"""

def addSummaryPDF(String zdbId, String pmcId, pubYear) {

    def dir = new File("${System.getenv()['LOADUP_FULL_PATH']}/$pubYear/$zdbId/")

    dir.eachFileRecurse(FileType.FILES) { file ->
        if (file.name.endsWith('.pdf')) {
            ADD_BASIC_PDFS_TO_DB.append([zdbId, pmcId, pubYear + "/" + zdbId + "/" + file.name, file.name].join('|') + "\n")
        }
    }

}

def downloadPMCFileBundle(String url, String zdbId, String pubYear) {
    def timeStart = new Date()
    def yearDirectory = new File("${System.getenv()['LOADUP_FULL_PATH']}/$pubYear/")
    def directory = new File("${System.getenv()['LOADUP_FULL_PATH']}/$pubYear/$zdbId")
    if (!yearDirectory.exists()) {
        yearDirectory.mkdir()
    }
    if (!directory.exists()) {
        directory.mkdir()
    }

    def file = new FileOutputStream("${System.getenv()['LOADUP_FULL_PATH']}/$pubYear/$zdbId/$zdbId" + ".tar.gz")
    def out = new BufferedOutputStream(file)

    out << new URL(url).openStream()
    out.close()
    def timeStop = new Date()
    TimeDuration duration = TimeCategory.minus(timeStop, timeStart)
    println("download to filesystem duration:" + duration)

    def gziped_bundle = "${System.getenv()['LOADUP_FULL_PATH']}/$pubYear/$zdbId/$zdbId" + ".tar.gz"
    def unzipped_output = "${System.getenv()['LOADUP_FULL_PATH']}/$pubYear/$zdbId/$zdbId" + ".tar"
    File unzippedFile = new File(unzipped_output)
    if (!unzippedFile.exists()) {
        PubmedUtils.gunzip(gziped_bundle, unzipped_output)
    }

    def timeStart2 = new Date()
    def cmd = "cd " + "${System.getenv()['LOADUP_FULL_PATH']}/$pubYear/$zdbId/ " + "&& /bin/tar -xf *.tar --strip 1"
    ["/bin/bash", "-c", cmd].execute().waitFor()

    def timeStop2 = new Date()
    TimeDuration duration2 = TimeCategory.minus(timeStop2, timeStart2)
    println("extract file duration:" + duration2)
}

def downloadNonOpenAccessPDF (String pmcId, String zdbId, String pubYear) {

    def timeStart = new Date()
    def yearDirectory = new File("${System.getenv()['LOADUP_FULL_PATH']}/$pubYear/")
    def directory = new File("${System.getenv()['LOADUP_FULL_PATH']}/$pubYear/$zdbId")

    def code = new URL("https://www.ncbi.nlm.nih.gov/pmc/articles/" + pmcId + "/pdf/").openConnection().with {
        requestMethod = 'HEAD'
        addRequestProperty("user-agent", "Zebrafish Information Network (ZFIN)")
        connect()
        responseCode
    }

    if (code.equals(200)) {
        if (!yearDirectory.exists()) {
            yearDirectory.mkdir()
        }
        if (!directory.exists()) {
            directory.mkdir()
        }
        def file = new FileOutputStream("${System.getenv()['LOADUP_FULL_PATH']}/$pubYear/$zdbId/$zdbId" +".pdf")
        def out = new BufferedOutputStream(file)

        URLConnection connection = new URL("https://www.ncbi.nlm.nih.gov/pmc/articles/" + pmcId + "/pdf/").openConnection()
        connection.setRequestProperty("user-agent", "Zebrafish Information Network (ZFIN)")
        out << connection.getInputStream()
        out.close()
        def timeStop = new Date()
        TimeDuration duration = TimeCategory.minus(timeStop, timeStart)
        println("Non open access download to filesystem duration:" + duration)
        ADD_BASIC_PDFS_TO_DB.append([zdbId, pmcId, pubYear + "/" + zdbId + "/" + zdbId + ".pdf", zdbId + ".pdf"].join('|') + "\n")
    }
}

def processPMCText(GPathResult pmcTextArticle, String zdbId, String pmcId, String pubYear) {
    def article = pmcTextArticle.GetRecord.record.metadata.article
    def header = pmcTextArticle.GetRecord.record.header
    header.setSpec.each { setspec ->
        if (setspec == 'npgopen' || setspec == 'pmc-open') {
            PUBS_TO_GIVE_PERMISSIONS.append([zdbId].join('|') + "\n")
        }
    }
    def markedUpBody = new StreamingMarkupBuilder().bindNode(article.body).toString()
    def imageFilePath = "${System.getenv()['LOADUP_FULL_PATH']}/$pubYear/$zdbId/"
    def tagMatch = markedUpBody =~ /<([^\/]*?):body/

    if (tagMatch.size() == 1) {

        def tag = tagMatch[0][1]  // extract the XML namespace tag pattern for use in extracting supplements, figures, images downstream.

        def supplimentPattern = "<${tag}:supplementary-material content-type=(.*?)</${tag}:supplementary-material>"
        def supplimentMatches = markedUpBody =~ /${supplimentPattern}/
        if (supplimentMatches.size() > 0) {
            supplimentMatches.each {
                def supplement = it
                if (pmcId == 'PMC:4679720') {
                    println(it)
                }
                def filenamePattern = "<${tag}:media xlink:href='(.*?)'"
                def filenameMatch = supplimentMatches =~ /${filenamePattern}/
                if (filenameMatch.size() > 0) {
                    def filename = filenameMatch[0][1]
                    if (filename.endsWith(".avi") || filename.endsWith(".mp4") || filename.endsWith(".mov") || filename.endsWith(".wmv")) {
                        parseLabelCaptionImage(supplement,zdbId,pmcId,imageFilePath,pubYear, tag)
                        println("videos")
                        println(filename)
                    } else {
                        PUB_FILES_TO_LOAD.append([zdbId, pmcId, pubYear + "/" + zdbId + "/" + filename, filename].join('|') + "\n")
                    }
                }
            }
        }
        // extract figures one by one from the grouping 'fig' tags
        def figPattern = "<${tag}:fig(.*?)>(.*?)</${tag}:fig>"
        def figMatches = markedUpBody =~ /${figPattern}/

        if (figMatches.size() > 0) {
            figMatches.each {
                def entireFigString = it[0]
                parseLabelCaptionImage(entireFigString, zdbId, pmcId, imageFilePath, pubYear, tag)
            }
        }
        else { // means the publisher pulls its figures into one section of the XML under the tag 'floats-group'
            def floatsGroup = new StreamingMarkupBuilder().bindNode(article["floats-group"]).toString()
            def fgFigPattern = "<${tag}:fig(.*?)>(.*?)</${tag}:fig>"
            def fgFigMatches = floatsGroup =~ /${fgFigPattern}/
            println("floatsGroup images")

            if (fgFigMatches.size() > 0) {
                fgFigMatches.each {
                    def entireFigString = it[0]
                    parseLabelCaptionImage(entireFigString, zdbId, pmcId, imageFilePath, pubYear, tag)
                }
            }
        }
        addSummaryPDF(zdbId, pmcId, pubYear)
    }
}

def parseLabelCaptionImage(groupMatchString, zdbId, pmcId, imageFilePath, pubYear, tag) {

    def entireFigString = groupMatchString
    def label = ''
    def caption = ''
    def image = ''
    def labelPattern = "<${tag}:label>(.*?)</${tag}:label>"
    def labelMatch = entireFigString =~ /${labelPattern}/
    if (labelMatch.size() > 0) {
        labelMatch.each {
            label = it[1]
        }
    }
    def captionPattern = "<${tag}:caption>(.*?)</${tag}:caption>"
    def captionMatch = entireFigString =~ /${captionPattern}/
    if (captionMatch.size() > 0) {
        captionMatch.each {
            caption = it[1]
            caption = caption.replace(tag + ":", '')
            caption = caption.replaceAll("\\s{2,}", " ")
            caption = caption.replace("|", "&&&&&")
        }
    }
    def imagePattern = "<${tag}:graphic(.*?)xlink:href='(.*?)'"
    def imageNameMatch = entireFigString =~ /${imagePattern}/

    if (imageNameMatch.size() > 0) {
        imageNameMatch.each {
            image = it[2] + ".jpg"
            println (image)
            makeThumbnailAndMediumImage(image, image.replace(".jpg", ""), zdbId, pubYear)
            String extension = FilenameUtils.getExtension(image)
            String fileNameNoExtension = image.replace(".jpg", "")
            String thumbnailFilename = fileNameNoExtension + "_thumb" + FilenameUtils.EXTENSION_SEPARATOR + extension
            String mediumFileName = fileNameNoExtension + "_medium" + FilenameUtils.EXTENSION_SEPARATOR + extension
            FIGS_TO_LOAD.append([zdbId, pmcId, image, label, caption, pubYear + "/" + zdbId + "/" + image,
                                 pubYear + "/" + zdbId + "/" + thumbnailFilename,
                                 pubYear + "/" + zdbId + "/" + mediumFileName].join('|') + "\n")
        }
    }
}


def makeThumbnailAndMediumImage(fileName, fileNameNoExtension, pubZdbId, pubYear) {

    String extension = FilenameUtils.getExtension(fileName)

    String thumbnailFilename = fileNameNoExtension + "_thumb" + FilenameUtils.EXTENSION_SEPARATOR + extension
    String mediumFileName = fileNameNoExtension + "_medium" + FilenameUtils.EXTENSION_SEPARATOR + extension
    File thumbnailFile = new File(ZfinPropertiesEnum.LOADUP_FULL_PATH.toString()+"/"+pubYear+"/"+pubZdbId+"/", thumbnailFilename)
    File mediumFile = new File(ZfinPropertiesEnum.LOADUP_FULL_PATH.toString()+"/"+pubYear+"/"+pubZdbId+"/", mediumFileName)
    File fullFile = new File(ZfinPropertiesEnum.LOADUP_FULL_PATH.toString()+"/"+pubYear+"/"+pubZdbId+"/", fileName)

    // make thumbnail and medium images in the same directory as their parent images.
    "/bin/convert -thumbnail 1000x64 ${fullFile} ${thumbnailFile}".execute()
    "/bin/convert -thumbnail 500x550 ${fullFile} ${mediumFile}".execute()

}

def fetchBundlesForExistingPubs(Map idsToGrab, File PUBS_WITH_PDFS_TO_UPDATE) {

    for (id in idsToGrab) {
        def zdbId = id.value
        def pmcId = id.key
        def pubYearMatch = zdbId =~ /^(ZDB-PUB-)(\d{2})(\d{2})(\d{2})(-\d+)$/
        def pubYear
        if (pubYearMatch.size() > 0) {
            pubYear = pubYearMatch[0][2]
            if (pubYear.toString().startsWith("9")) {
                pubYear = "19" + pubYear
            } else {
                pubYear = "20" + pubYear
            }
        }
        PubmedUtils.getPdfMetaDataRecord(pmcId).records.record.each { rec ->
            if (rec.link.@format.text() == 'tgz') {

                def pdfPath = rec.link.@href.text()
                PUBS_WITH_PDFS_TO_UPDATE.append(pdfPath + "\n")
                downloadPMCFileBundle(pdfPath, zdbId, pubYear)
                def fullTxt = PubmedUtils.getFullText(pmcId.toString().substring(3))
                println pmcId + "," + zdbId
                println (pdfPath)
                processPMCText(fullTxt, zdbId, pmcId, pubYear)
            }

            else {
                println "found a PDF to try and download manually " + pmcId + "," + zdbId
                downloadNonOpenAccessPDF(pmcId, zdbId, pubYear)
            }
        }
    }
}

new File(PUB_IDS_TO_CHECK).withReader { reader ->
    def lines = reader.iterator()
    lines.each { String line ->
        row = line.split(',')
        idsToGrab.put(row[0], row[1])
    }

}

fetchBundlesForExistingPubs(idsToGrab, PUBS_WITH_PDFS_TO_UPDATE)

givePubsPermissions = ['/bin/bash', '-c', "${ZfinPropertiesEnum.PGBINDIR}/psql -v ON_ERROR_STOP=1  " +
        "${ZfinPropertiesEnum.DB_NAME} -f ${WORKING_DIR.absolutePath}/give_pubs_permissions.sql " +
        ">${WORKING_DIR.absolutePath}/loadSQLOutput.log 2> ${WORKING_DIR.absolutePath}/loadSQLError.log"].execute()
givePubsPermissions.waitFor()

loadBasicPDFFiles = ['/bin/bash', '-c', "${ZfinPropertiesEnum.PGBINDIR}/psql -v ON_ERROR_STOP=1 " +
        "${ZfinPropertiesEnum.DB_NAME} -f ${WORKING_DIR.absolutePath}/add_basic_pdfs.sql " +
        ">${WORKING_DIR.absolutePath}/loadSQLOutput.log 2> ${WORKING_DIR.absolutePath}/loadSQLError.log"].execute()
loadBasicPDFFiles.waitFor()

loadFigsAndImages = ['/bin/bash', '-c', "${ZfinPropertiesEnum.PGBINDIR}/psql -v ON_ERROR_STOP=1 " +
        "${ZfinPropertiesEnum.DB_NAME} -f ${WORKING_DIR.absolutePath}/load_figs_and_images.sql " +
        ">${WORKING_DIR.absolutePath}/loadSQLOutput.log 2> ${WORKING_DIR.absolutePath}/loadSQLError.log"].execute()
loadFigsAndImages.waitFor()

loadPubFiles = ['/bin/bash', '-c', "${ZfinPropertiesEnum.PGBINDIR}/psql -v ON_ERROR_STOP=1 " +
        "${ZfinPropertiesEnum.DB_NAME} -f ${WORKING_DIR.absolutePath}/load_pub_files.sql " +
        ">${WORKING_DIR.absolutePath}/loadSQLOutput.log 2> ${WORKING_DIR.absolutePath}/loadSQLError.log"].execute()
loadPubFiles.waitFor()
