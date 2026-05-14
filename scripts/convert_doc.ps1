$ErrorActionPreference = 'Stop'
$src = 'D:\ENCORE\scripts\template.doc'
$dst = 'D:\ENCORE\ENCORE_SRS_V1_temp.docx'

if (Test-Path $dst) {
    Remove-Item $dst -Force
}

$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0

try {
    # Documents.Open(FileName, ConfirmConversions, ReadOnly, AddToRecentFiles, PasswordDocument, PasswordTemplate, Revert, WritePasswordDocument, WritePasswordTemplate, Format, Encoding, Visible, OpenAndRepair, DocumentDirection, NoEncodingDialog)
    $doc = $word.Documents.Open($src)
    Write-Host ("Opened doc with " + $doc.Paragraphs.Count + " paragraphs")
    # wdFormatXMLDocument = 16
    $doc.SaveAs2($dst, 16)
    $doc.Close($false)
    Write-Host ("OK: saved " + $dst)
} catch {
    Write-Host ("ERROR: " + $_.Exception.Message)
    throw
} finally {
    $word.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}
