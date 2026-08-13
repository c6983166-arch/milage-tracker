package com.baileyanns.mileage;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.os.ParcelFileDescriptor;
import android.print.*;

import java.io.*;
import java.util.*;

public final class YearEndReport {
    public static File create(Context c,int year,List<DatabaseHelper.Trip> trips)throws Exception{
        File f=new File(c.getCacheDir(),"Bailey_Anns_Mileage_Report_"+year+".pdf"); PdfDocument pdf=new PdfDocument(); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); int pageNo=1,y=0; PdfDocument.Page page=null; Canvas canvas=null;
        double estate=0,business=0; for(DatabaseHelper.Trip t:trips){ if("ESTATE".equals(t.tripType))estate+=t.miles;else business+=t.miles; }
        for(int idx=-1;idx<trips.size();idx++){
            if(page==null || y>730){ if(page!=null)pdf.finishPage(page); PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(612,792,pageNo++).create(); page=pdf.startPage(info); canvas=page.getCanvas(); y=50; p.setColor(Color.rgb(6,26,67));p.setTextSize(20);p.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText("BAILEY ANN'S ESTATE SOLUTIONS LLC",36,y,p);y+=25;p.setTextSize(16);canvas.drawText("YEAR-END BUSINESS MILEAGE REPORT — "+year,36,y,p);y+=26; p.setColor(Color.DKGRAY);p.setTextSize(10);p.setTypeface(Typeface.DEFAULT); }
            if(idx==-1){ p.setTextSize(12);p.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText(String.format(Locale.US,"Estate Miles: %.1f",estate),36,y,p);canvas.drawText(String.format(Locale.US,"Business Miles: %.1f",business),210,y,p);canvas.drawText(String.format(Locale.US,"Total: %.1f",estate+business),390,y,p);y+=28; p.setTextSize(9);p.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText("DATE",36,y,p);canvas.drawText("TYPE / ESTATE",95,y,p);canvas.drawText("PURPOSE",250,y,p);canvas.drawText("ODOMETER",410,y,p);canvas.drawText("MILES",548,y,p);y+=14;canvas.drawLine(36,y,576,y,p);y+=14; continue; }
            DatabaseHelper.Trip t=trips.get(idx); p.setTextSize(8.2f);p.setTypeface(Typeface.DEFAULT);p.setColor(Color.DKGRAY); canvas.drawText(s(t.date,16),36,y,p);String type="ESTATE".equals(t.tripType)?"Estate • "+t.estateName:"Business";canvas.drawText(s(type,27),95,y,p);canvas.drawText(s(t.purpose,28),250,y,p);String odo=(t.startOdometer>0||t.endOdometer>0)?String.format(Locale.US,"%s-%s",t.startOdometer>0?String.format(Locale.US,"%.0f",t.startOdometer):"—",t.endOdometer>0?String.format(Locale.US,"%.0f",t.endOdometer):"—"):"";canvas.drawText(s(odo,18),410,y,p);canvas.drawText(String.format(Locale.US,"%.1f",t.miles),548,y,p);y+=16;
        }
        if(page!=null)pdf.finishPage(page); try(FileOutputStream out=new FileOutputStream(f)){pdf.writeTo(out);} finally {pdf.close();} return f;
    }
    public static void print(Context c,File file,int year){ PrintManager pm=(PrintManager)c.getSystemService(Context.PRINT_SERVICE); pm.print("Bailey Ann's Mileage Report "+year,new FileAdapter(file),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.NA_LETTER).build()); }
    private static class FileAdapter extends PrintDocumentAdapter{ private final File file; FileAdapter(File f){file=f;} @Override public void onLayout(PrintAttributes oldA,PrintAttributes newA,android.os.CancellationSignal cs,LayoutResultCallback cb,android.os.Bundle extras){cb.onLayoutFinished(new PrintDocumentInfo.Builder(file.getName()).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),true);} @Override public void onWrite(android.print.PageRange[] pages,ParcelFileDescriptor dest,android.os.CancellationSignal cs,WriteResultCallback cb){try(FileInputStream in=new FileInputStream(file);FileOutputStream out=new FileOutputStream(dest.getFileDescriptor())){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);cb.onWriteFinished(new android.print.PageRange[]{android.print.PageRange.ALL_PAGES});}catch(Exception e){cb.onWriteFailed(e.getMessage());}} }
    private static String s(String x,int max){if(x==null)return "";return x.length()<=max?x:x.substring(0,Math.max(1,max-1))+"…";} private YearEndReport(){}
}
