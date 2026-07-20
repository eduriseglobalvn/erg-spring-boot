$ErrorActionPreference = 'Stop'
$source = 'D:\ERG\erg-spring\Tin 9 - mot bai moi chu de.docx'
$pdf = 'D:\ERG\erg-spring\Tin 9 - mot bai moi chu de.pdf'
New-Item -ItemType Directory -Force -Path (Split-Path $pdf) | Out-Null
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    $document = $word.Documents.Open($source, $false, $true)
    $document.ExportAsFixedFormat($pdf, 17)
    Write-Output ('Pages: ' + $document.ComputeStatistics(2))
    $document.Close()
} finally {
    $word.Quit()
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($word) | Out-Null
}
Write-Output $pdf
