Private Function FileName(s)
    nameWithPath = Split(s, ".")(0)
    If Len(nameWithPath) > 0 Then
        posA = InStrRev(nameWithPath, "/")
        posB = InStrRev(nameWithPath, "\")
        If posA > posB Then
            pos = posA
        Else
            pos = posB
        End If
        If pos > 2 Then
            FileName = Mid(nameWithPath, pos + 1)
        Else
            FileName = nameWithPath
        End If
    End If
    
End Function

If Wscript.Arguments.Count > 0 Then
    strArg = Wscript.Arguments(0)
    Set objWSH = CreateObject("WScript.Shell")
    objWSH.Run "cmd /C copy " + strArg + " \\localhost\" + FileName(strArg), 0, True
    objWSH.Run "cmd /C del " + strArg, 0, True
End If