$reviewsJson = Get-Content -Raw -Path "reviews_to_inject.json" | ConvertFrom-Json
foreach ($review in $reviewsJson) {
    $body = $review | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "http://localhost:8082/api/reviews" -Method Post -Body $body -ContentType "application/json"
    Write-Host "Injected review for $($review.productId)"
}
